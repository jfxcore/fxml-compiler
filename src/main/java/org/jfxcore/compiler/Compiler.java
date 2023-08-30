// Copyright (c) 2022, 2023, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.NotFoundException;
import javassist.bytecode.BadBytecode;
import javassist.bytecode.MethodInfo;
import org.jfxcore.compiler.ast.DocumentNode;
import org.jfxcore.compiler.ast.codebehind.ClassNode;
import org.jfxcore.compiler.ast.codebehind.JavaEmitContext;
import org.jfxcore.compiler.ast.emit.BytecodeEmitContext;
import org.jfxcore.compiler.ast.emit.EmitInitializeRootNode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.diagnostic.errors.SymbolResolutionErrors;
import org.jfxcore.compiler.parse.FxmlParseAbortException;
import org.jfxcore.compiler.parse.FxmlParser;
import org.jfxcore.compiler.transform.Transformer;
import org.jfxcore.compiler.util.AbstractCompiler;
import org.jfxcore.compiler.util.Bytecode;
import org.jfxcore.compiler.util.CompilationContext;
import org.jfxcore.compiler.util.CompilationScope;
import org.jfxcore.compiler.util.CompilationSource;
import org.jfxcore.compiler.util.ExceptionHelper;
import org.jfxcore.compiler.util.FileUtil;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Compiler extends AbstractCompiler {

    private enum Stage {
        PARSE,
        GENERATE_SOURCES,
        COMPILE,
        FINISHED
    }

    private final Logger logger;
    private final ClassPool classPool;
    private final Transformer transformer;
    private final Path generatedSourcesDir;
    private final Map<Path, DocumentNode> documents = new HashMap<>();
    private final Map<Path, Compilation> compilations = new HashMap<>();
    private final Map<Path, DocumentNode> classDocuments = new HashMap<>();
    private final Map<String, List<ClassInfo>> generatedClasses = new HashMap<>();
    private Stage stage = Stage.PARSE;

    public Compiler(Path generatedSourcesDir, Set<File> classpath, Logger logger) {
        this.logger = logger;
        this.classPool = newClassPool(classpath);
        this.transformer = Transformer.getCodeTransformer(classPool);
        this.generatedSourcesDir = generatedSourcesDir.normalize();
    }

    /**
     * Adds an FXML markup file to the current compiler, and returns a path that points to
     * the generated Java file that corresponds to the FXML markup file.
     *
     * @param sourceDir the base source directory (the root of the package namespace)
     * @param sourceFile the FXML markup file
     * @return a path that points to the generated Java file, or {@code null} if the
     *         markup file will not be compiled
     * @throws IOException if an I/O error occurs
     * @throws MarkupException if a markup error occurs
     */
    @SuppressWarnings("unused")
    public Path addFile(Path sourceDir, Path sourceFile) throws IOException {
        if (stage != Stage.PARSE) {
            throw new IllegalStateException("Cannot add file in stage " + stage);
        }

        sourceFile = sourceFile.normalize();

        try {
            sourceDir.normalize().relativize(sourceFile);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("The specified file is not in the source directory.");
        }

        if (compilations.containsKey(sourceFile)) {
            throw new IllegalArgumentException("The specified file was already added.");
        }

        try {
            DocumentNode document = new FxmlParser(sourceDir, sourceFile).parseDocument();
            documents.put(sourceFile, document);
            return generatedSourcesDir.resolve(FileUtil.getMarkupJavaFile(document));
        } catch (FxmlParseAbortException ex) {
            logger.info(String.format("File skipped: %s (%s)", sourceFile, ex.getMessage()));
            return null;
        } catch (MarkupException ex) {
            ex.setSourceFile(sourceFile.toFile());
            throw ex;
        }
    }

    /**
     * Generates Java stub files for all FXML markup files that were added to this compiler.
     * <p>
     * This is the first step of compilation.
     *
     * @throws IOException if an I/O error occurs
     * @throws MarkupException if a markup error occurs
     */
    @SuppressWarnings("unused")
    public void processFiles() throws IOException {
        if (stage != Stage.PARSE) {
            throw new IllegalStateException("Cannot generate sources in stage " + stage);
        }

        stage = Stage.COMPILE;

        for (var entry : documents.entrySet()) {
            Path sourceFile = entry.getKey();
            CompilationContext context = new CompilationContext(new CompilationSource.FileSystem(sourceFile));

            try (var ignored = new CompilationScope(context)) {
                compilations.put(sourceFile, new Compilation(entry.getValue(), context));
                parseSingleFile(sourceFile, entry.getValue(), transformer);
            } catch (MarkupException ex) {
                ex.setSourceFile(sourceFile.toFile());
                throw ex;
            }
        }

        for (var entry : generatedClasses.entrySet()) {
            String packageName = entry.getKey();
            Path outputDir = generatedSourcesDir.resolve(packageName.replace(".", "/"));
            deleteFiles(outputDir);
            writeClasses(outputDir, entry.getValue());
        }
    }

    /**
     * Compiles the content of the FXML markup files that were added to this compiler.
     * <p>
     * This is the second step of compilation.
     *
     * @throws IOException if an I/O error occurs
     * @throws MarkupException if a markup error occurs
     */
    @SuppressWarnings("unused")
    public void compileFiles() throws IOException {
        if (stage != Stage.COMPILE) {
            throw new IllegalStateException("Cannot compile in stage " + stage);
        }

        stage = Stage.FINISHED;
        Transformer transformer = null;

        for (Path sourceFile : classDocuments.keySet()) {
            if (transformer == null) {
                transformer = Transformer.getBytecodeTransformer(classPool);
            }

            compileSingleFile(sourceFile, transformer);
        }
    }

    private void deleteFiles(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }

        try (var files = Files.walk(directory, 1)) {
            files.forEach(f -> {
                if (Files.isRegularFile(f)) {
                    try {
                        Files.delete(f);
                    } catch (IOException e) {
                        logger.error("Cannot delete file " + f);
                    }
                }
            });
        }
    }

    private void parseSingleFile(Path inputFile, DocumentNode document, Transformer transformer) {
        logger.debug("Parsing " + inputFile);
        StringBuilder stringBuilder = new StringBuilder();
        JavaEmitContext context = new JavaEmitContext(stringBuilder);

        try {
            document = (DocumentNode)transformer.transform(document, null, null);
            context.emit(document);
        } catch (MarkupException ex) {
            ex.setSourceFile(inputFile.toFile());
            throw ex;
        }

        classDocuments.put(inputFile, document);
        ClassNode classNode = ((ClassNode)document.getRoot());
        String packageName = classNode.getPackageName();
        generatedClasses.putIfAbsent(packageName, new ArrayList<>());
        generatedClasses.get(packageName).add(new ClassInfo(classNode, inputFile, stringBuilder.toString()));
    }

    private void writeClasses(Path outputDir, List<ClassInfo> classes) throws IOException {
        Files.createDirectories(outputDir);

        for (ClassInfo classInfo : classes) {
            Path outputFile = outputDir.resolve(
                classInfo.classNode().hasCodeBehind() ?
                classInfo.classNode().getMarkupClassName() + ".java" :
                classInfo.classNode().getClassName() + ".java");

            logger.debug("Generating " + outputFile);

            Files.writeString(
                outputFile,
                classInfo.sourceText(),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);
        }
    }

    private void compileSingleFile(Path inputFile, Transformer transformer) throws IOException {
        logger.debug("Compiling " + inputFile);
        Compilation compilation = compilations.get(inputFile);

        try (var ignored = new CompilationScope(compilation.context())) {
            ClassNode classNode = (ClassNode)classDocuments.get(inputFile).getRoot();
            boolean hasCodeBehind = classNode.hasCodeBehind();
            String packageName = classNode.getPackageName();
            String codeBehindClassName = packageName + "." + classNode.getClassName();
            String simpleMarkupClassName = hasCodeBehind ? classNode.getMarkupClassName() : classNode.getClassName();
            String markupClassName = packageName + "." + simpleMarkupClassName;
            URL classUrl = transformer.getClassPool().find(markupClassName);

            if (classUrl == null) {
                throw new RuntimeException(String.format("%s cannot be found on the classpath", markupClassName));
            }

            CtClass codeBehindClass = transformer.getClassPool().get(codeBehindClassName);
            CtClass markupClass = transformer.getClassPool().get(markupClassName);
            markupClass.defrost();

            Bytecode bytecode = new Bytecode(markupClass, 1);

            EmitInitializeRootNode rootNode = (EmitInitializeRootNode)transformer.transform(
                compilations.get(inputFile).document(), codeBehindClass, markupClass);

            BytecodeEmitContext emitContext = new BytecodeEmitContext(
                codeBehindClass, markupClass, rootNode, compilations.get(inputFile).document().getImports(), bytecode);

            emitContext.emitRootNode();

            MethodInfo methodInfo = markupClass.getClassFile().getMethod("initializeComponent");
            methodInfo.setCodeAttribute(bytecode.toCodeAttribute());
            methodInfo.rebuildStackMap(markupClass.getClassPool());

            Path outDir = Paths.get(classUrl.toURI());
            compilation.context().addModifiedClass(markupClass, outDir);
            emitContext.getNestedClasses().forEach(c -> compilation.context().addModifiedClass(c, outDir));

            flushModifiedClasses(compilation.context());
        } catch (MarkupException ex) {
            ex.setSourceFile(inputFile.toFile());
            throw ex;
        } catch (BadBytecode | URISyntaxException | CannotCompileException ex) {
            throw ExceptionHelper.unchecked(ex);
        } catch (NotFoundException ex) {
            MarkupException m = SymbolResolutionErrors.classNotFound(SourceInfo.none(), ex.getMessage());
            m.setSourceFile(inputFile.toFile());
            throw m;
        }
    }

    private ClassPool newClassPool(Set<File> classpath) {
        ClassPool classPool = new ClassPool(true);

        for (File cp : classpath) {
            try {
                classPool.appendClassPath(cp.getAbsolutePath());
            } catch (NotFoundException ex) {
                throw new RuntimeException("Search path dependency not found: " + ex.getMessage());
            }
        }

        return classPool;
    }

    private record ClassInfo(ClassNode classNode, Path sourceFile, String sourceText) {}
    private record Compilation(DocumentNode document, CompilationContext context) {}

}
