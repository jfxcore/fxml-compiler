// Copyright (c) 2021, JFXcore. All rights reserved.
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
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Compiler extends AbstractCompiler {

    private static final String[] EXTENSIONS = new String[] {".fxml"};

    private enum Stage {
        PARSE,
        GENERATE_SOURCES,
        COMPILE,
        FINISHED
    }

    private final Logger logger;
    private final Set<File> classpath;
    private final Map<Path, Compilation> compilations = new HashMap<>();
    private final Map<Path, DocumentNode> classDocuments = new HashMap<>();
    private final Map<String, List<ClassInfo>> generatedClasses = new HashMap<>();
    private Stage stage = Stage.PARSE;

    public Compiler(Set<File> classpath, Logger logger) {
        this.classpath = classpath;
        this.logger = logger;
    }

    @SuppressWarnings("unused")
    public void parseFiles(File sourceDir) throws IOException {
        if (stage != Stage.PARSE) {
            throw new IllegalStateException("Cannot parse files in stage " + stage);
        }

        Transformer transformer = Transformer.getCodeTransformer(newClassPool(classpath));

        for (Path sourceFile : FileUtil.enumerateFiles(sourceDir.toPath(), this::fileFilter)) {
            CompilationContext context = new CompilationContext(new CompilationSource.FileSystem(sourceFile));

            try (var ignored = new CompilationScope(context)) {
                DocumentNode document = new FxmlParser(sourceDir.toPath(), sourceFile).parseDocument();
                compilations.put(sourceFile, new Compilation(document, context));
                parseSingleFile(sourceFile, document, transformer);
            } catch (FxmlParseAbortException ex) {
                logger.info(String.format("File skipped: %s (%s)", sourceFile, ex.getMessage()));
            } catch (MarkupException ex) {
                ex.setSourceFile(sourceFile.toFile());
                throw ex;
            }
        }
    }

    @SuppressWarnings("unused")
    public void generateSources(File generatedSourcesDir) throws IOException {
        if (stage != Stage.PARSE) {
            throw new IllegalStateException("Cannot generate sources in stage " + stage);
        }

        stage = Stage.COMPILE;

        for (var entry : generatedClasses.entrySet()) {
            String packageName = entry.getKey();
            Path outputDir = generatedSourcesDir.toPath().resolve(packageName.replace(".", "/"));
            deleteFiles(outputDir);
            writeClasses(outputDir, entry.getValue());
            writeAccessorClass(outputDir, packageName, entry.getValue());
        }
    }

    @SuppressWarnings("unused")
    public void compileFiles() throws IOException {
        if (stage != Stage.COMPILE) {
            throw new IllegalStateException("Cannot compile in stage " + stage);
        }

        stage = Stage.FINISHED;
        Transformer transformer = null;

        for (Path sourceFile : classDocuments.keySet()) {
            if (transformer == null) {
                transformer = Transformer.getBytecodeTransformer(newClassPool(classpath));
            }

            compileSingleFile(sourceFile, transformer);
        }
    }

    private boolean fileFilter(Path path) {
        String file = path.toString().toLowerCase();
        return Arrays.stream(EXTENSIONS).anyMatch(file::endsWith);
    }

    private void deleteFiles(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }

        Files.walk(directory, 1).forEach(f -> {
            if (Files.isRegularFile(f)) {
                try {
                    Files.delete(f);
                } catch (IOException e) {
                    logger.error("Cannot delete file " + f);
                }
            }
        });
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
                classInfo.classNode().getMangledClassName() + ".java" :
                classInfo.classNode().getClassName() + ".java");

            logger.debug("Generating " + outputFile);

            Files.writeString(
                outputFile,
                classInfo.sourceText(),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);
        }
    }

    private void writeAccessorClass(Path outputDir, String packageName, List<ClassInfo> classes) throws IOException {
        if (classes.stream().noneMatch(c -> c.classNode().hasCodeBehind())) {
            return;
        }

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder
            .append("//\r\n")
            .append(String.format("// This file was generated by jfxcore-compiler-%s\r\n",
                    org.jfxcore.compiler.VersionInfo.getVersion()))
            .append("// Changes in this file will be lost if the code is regenerated.\r\n")
            .append("//\r\n");

        if (!packageName.isEmpty()) {
            stringBuilder.append(String.format("package %s;\r\n", packageName));
        }

        stringBuilder.append("\r\n");
        stringBuilder.append("/** Contains all compiler-generated markup classes in this package. */\r\n");
        stringBuilder.append(String.format("%sfinal class markup {\r\n", getModifierString(classes)));
        stringBuilder.append("\tprivate markup() {}\r\n");

        for (ClassInfo classInfo : classes) {
            if (!classInfo.classNode().hasCodeBehind()) {
                continue;
            }

            stringBuilder.append(
                String.format(
                    "\t%sstatic abstract class %s extends %s {\r\n",
                    getModifierString(List.of(classInfo)),
                    classInfo.classNode().getClassName(),
                    classInfo.classNode().getMangledClassName()));

            String[] arguments = classInfo.classNode().getParameters();
            if (arguments.length > 0) {
                Integer[] n = intSequence(arguments.length);

                stringBuilder
                    .append("\r\n")
                    .append(String.format("\t\tpublic %s(", classInfo.classNode().getClassName()))
                    .append(makeList("%s arg%s", arguments, n))
                    .append(") {\r\n")
                    .append(String.format("\t\t\tsuper(%s);\r\n", makeList("arg%s", n, null)))
                    .append("\t\t}\r\n\t");
            }

            stringBuilder.append("\t}\r\n");
        }

        stringBuilder.append("}\r\n");

        Path outputFile = outputDir.resolve("markup.java");

        logger.debug("Generating " + outputFile);

        Files.writeString(
            outputFile,
            stringBuilder.toString(),
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING);
    }

    private String makeList(String format, Object[] args1, Object[] args2) {
        StringBuilder stringBuilder = new StringBuilder();

        for (int i = 0; i < args1.length; ++i) {
            if (i > 0) {
                stringBuilder.append(", ");
            }

            if (args2 != null) {
                stringBuilder.append(String.format(format, args1[i], args2[i]));
            } else {
                stringBuilder.append(String.format(format, args1[i]));
            }
        }

        return stringBuilder.toString();
    }

    private Integer[] intSequence(int n) {
        Integer[] res = new Integer[n];
        for (int i = 0; i < n; ++i) {
            res[i] = i;
        }

        return res;
    }

    private String getModifierString(List<ClassInfo> classes) {
        if (classes.stream().anyMatch(c -> Modifier.isPublic(c.classNode().getClassModifiers()))) {
            return "public ";
        } else if (classes.stream().anyMatch(c -> Modifier.isProtected(c.classNode().getClassModifiers()))) {
            return "protected ";
        } else {
            return "";
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
            String simpleMarkupClassName = hasCodeBehind ? classNode.getMangledClassName() : classNode.getClassName();
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
            MarkupException m = SymbolResolutionErrors.notFound(SourceInfo.none(), ex.getMessage());
            m.setSourceFile(inputFile.toFile());
            throw m;
        }
    }

    private ClassPool newClassPool(Set<File> classpath) {
        ClassPool classPool = new ClassPool();
        classPool.appendSystemPath();

        for (File cp : classpath) {
            try {
                classPool.appendClassPath(cp.getAbsolutePath());
            } catch (NotFoundException ex) {
                throw new RuntimeException(ex.getMessage());
            }
        }

        return classPool;
    }

    private record ClassInfo(ClassNode classNode, Path sourceFile, String sourceText) {}
    private record Compilation(DocumentNode document, CompilationContext context) {}

}
