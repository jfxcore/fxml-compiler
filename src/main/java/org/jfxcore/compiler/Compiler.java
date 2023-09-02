// Copyright (c) 2022, 2023, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler;

import javassist.CannotCompileException;
import javassist.ClassPath;
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
import org.jfxcore.compiler.diagnostic.errors.GeneralErrors;
import org.jfxcore.compiler.diagnostic.errors.SymbolResolutionErrors;
import org.jfxcore.compiler.parse.FxmlParseAbortException;
import org.jfxcore.compiler.parse.FxmlParser;
import org.jfxcore.compiler.transform.Transformer;
import org.jfxcore.compiler.util.AbstractCompiler;
import org.jfxcore.compiler.util.Bytecode;
import org.jfxcore.compiler.util.CompilationContext;
import org.jfxcore.compiler.util.CompilationScope;
import org.jfxcore.compiler.util.CompilationSource;
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
import java.util.Objects;
import java.util.Set;

public class Compiler extends AbstractCompiler implements AutoCloseable {

    private enum Stage {
        ADD_FILES,
        COMPILE,
        FINISHED
    }

    private final Logger logger;
    private final Set<File> searchPath;
    private final List<ClassPath> classPaths;
    private final Path generatedSourcesDir;
    private final Map<Path, Compilation> compilations = new HashMap<>();
    private final Map<String, List<ClassInfo>> generatedClasses = new HashMap<>();
    private ClassPool classPool;
    private Stage stage = Stage.ADD_FILES;

    static {
        // If this flag is set to true, file handles for JAR files are sometimes not released.
        ClassPool.cacheOpenedJarFile = false;
    }

    public Compiler(Path generatedSourcesDir, Set<File> searchPath, Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.searchPath = Objects.requireNonNull(searchPath, "searchPath");
        this.generatedSourcesDir = Objects.requireNonNull(generatedSourcesDir, "generatedSourcesDir").normalize();
        this.classPaths = new ArrayList<>();
    }

    @Override
    public void close() {
        if (classPool != null) {
            classPaths.forEach(classPool::removeClassPath);
        }
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
        Objects.requireNonNull(sourceDir, "sourceDir");
        sourceFile = Objects.requireNonNull(sourceFile, "sourceFile").normalize();

        if (stage != Stage.ADD_FILES) {
            throw new IllegalStateException("Cannot add file in stage " + stage);
        }

        try {
            sourceDir.normalize().relativize(sourceFile);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("The specified file is not in the source directory.");
        }

        if (compilations.containsKey(sourceFile)) {
            throw new IllegalArgumentException("The specified file was already added.");
        }

        CompilationContext context = new CompilationContext(new CompilationSource.FileSystem(sourceFile)) {
            @Override
            public Logger getLogger() {
                return logger;
            }
        };

        try (var ignored = new CompilationScope(context)) {
            DocumentNode document = new FxmlParser(sourceDir, sourceFile).parseDocument();
            compilations.put(sourceFile, new Compilation(document, null, context));
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
        if (stage != Stage.ADD_FILES) {
            throw new IllegalStateException("Cannot process files in stage " + stage);
        }

        stage = Stage.COMPILE;

        if (compilations.isEmpty()) {
            return;
        }

        ensureClassPool();

        var transformer = Transformer.getCodeTransformer(classPool);

        for (var entry : Map.copyOf(compilations).entrySet()) {
            Path sourceFile = entry.getKey();
            DocumentNode document = entry.getValue().document();
            CompilationContext context = entry.getValue().context();

            try (var ignored = new CompilationScope(context)) {
                parseSingleFile(sourceFile, document, transformer);
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

    private void parseSingleFile(Path inputFile, DocumentNode document, Transformer transformer) {
        logger.fine("Parsing FXML markup file " + inputFile);
        StringBuilder stringBuilder = new StringBuilder();
        JavaEmitContext context = new JavaEmitContext(stringBuilder);

        try {
            document = (DocumentNode)transformer.transform(document, null, null);
            context.emit(document);
        } catch (MarkupException ex) {
            ex.setSourceFile(inputFile.toFile());
            throw ex;
        }

        ClassNode classNode = ((ClassNode)document.getRoot());
        String packageName = classNode.getPackageName();
        generatedClasses.putIfAbsent(packageName, new ArrayList<>());
        generatedClasses.get(packageName).add(new ClassInfo(classNode, inputFile, stringBuilder.toString()));
        compilations.computeIfPresent(inputFile, (path, comp) -> comp.withRootClass(classNode));
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

    private void writeClasses(Path outputDir, List<ClassInfo> classes) throws IOException {
        Files.createDirectories(outputDir);

        for (ClassInfo classInfo : classes) {
            Path outputFile = outputDir.resolve(
                classInfo.classNode().hasCodeBehind() ?
                classInfo.classNode().getMarkupClassName() + ".java" :
                classInfo.classNode().getClassName() + ".java");

            logger.fine("Generating FXML code file " + outputFile);

            Files.writeString(
                outputFile,
                classInfo.sourceText(),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);
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
            throw new IllegalStateException("Cannot compile files in stage " + stage);
        }

        stage = Stage.FINISHED;

        if (compilations.isEmpty()) {
            return;
        }

        var transformer = Transformer.getBytecodeTransformer(classPool);

        for (var entry : compilations.entrySet()) {
            compileSingleFile(entry.getKey(), entry.getValue(), transformer);
        }
    }

    private void compileSingleFile(Path sourceFile, Compilation compilation, Transformer transformer)
            throws IOException {
        try (var ignored = new CompilationScope(compilation.context())) {
            ClassNode classNode = compilation.rootClass();
            boolean hasCodeBehind = classNode.hasCodeBehind();
            String packageName = classNode.getPackageName();
            String codeBehindClassName = packageName + "." + classNode.getClassName();
            String simpleMarkupClassName = hasCodeBehind ? classNode.getMarkupClassName() : classNode.getClassName();
            String markupClassName = packageName + "." + simpleMarkupClassName;
            logger.fine(String.format("Compiling FXML class '%s'", markupClassName));

            URL classUrl = transformer.getClassPool().find(markupClassName);
            if (classUrl == null) {
                throw GeneralErrors.internalError(String.format("%s cannot be found", markupClassName));
            }

            CtClass codeBehindClass = transformer.getClassPool().get(codeBehindClassName);
            CtClass markupClass = transformer.getClassPool().get(markupClassName);
            markupClass.defrost();

            Bytecode bytecode = new Bytecode(markupClass, 1);

            EmitInitializeRootNode rootNode = (EmitInitializeRootNode)transformer.transform(
                compilation.document(), codeBehindClass, markupClass);

            BytecodeEmitContext emitContext = new BytecodeEmitContext(
                codeBehindClass, markupClass, rootNode, compilation.document().getImports(), bytecode);

            emitContext.emitRootNode();

            MethodInfo methodInfo = markupClass.getClassFile().getMethod("initializeComponent");
            if (methodInfo == null) {
                throw GeneralErrors.internalError("Invalid markup class file");
            }

            methodInfo.setCodeAttribute(bytecode.toCodeAttribute());
            methodInfo.rebuildStackMap(markupClass.getClassPool());

            Path outDir = Paths.get(classUrl.toURI());
            compilation.context().addModifiedClass(markupClass, outDir);
            emitContext.getNestedClasses().forEach(c -> compilation.context().addModifiedClass(c, outDir));

            flushModifiedClasses(compilation.context());
        } catch (MarkupException ex) {
            ex.setSourceFile(sourceFile.toFile());
            throw ex;
        } catch (BadBytecode | URISyntaxException | CannotCompileException ex) {
            MarkupException m = GeneralErrors.internalError(ex.getMessage());
            m.setSourceFile(sourceFile.toFile());
            throw m;
        } catch (NotFoundException ex) {
            MarkupException m = SymbolResolutionErrors.classNotFound(SourceInfo.none(), ex.getMessage());
            m.setSourceFile(sourceFile.toFile());
            throw m;
        }
    }

    private void ensureClassPool() {
        if (classPool != null) {
            return;
        }

        classPool = new ClassPool(true);

        for (File path : searchPath) {
            try {
                classPaths.add(classPool.appendClassPath(path.getAbsolutePath()));
            } catch (NotFoundException ex) {
                throw new RuntimeException("Search path dependency not found: " + ex.getMessage());
            }
        }
    }

    private record ClassInfo(ClassNode classNode, Path sourceFile, String sourceText) {}
    private record Compilation(DocumentNode document, ClassNode rootClass, CompilationContext context) {
        Compilation withRootClass(ClassNode rootClass) {
            return new Compilation(document, rootClass, context);
        }
    }

}
