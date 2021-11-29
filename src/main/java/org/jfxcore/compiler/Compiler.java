// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.NotFoundException;
import javassist.bytecode.BadBytecode;
import javassist.bytecode.MethodInfo;
import org.jfxcore.compiler.ast.codebehind.JavaEmitContext;
import org.jfxcore.compiler.ast.emit.BytecodeEmitContext;
import org.jfxcore.compiler.ast.DocumentNode;
import org.jfxcore.compiler.ast.codebehind.ClassNode;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Compiler extends AbstractCompiler {

    private static final String[] EXTENSIONS = new String[] {".fxml"};

    private final Logger logger;
    private final Set<Path> skippedFiles = new HashSet<>();
    private final Map<Path, Compilation> compilations = new HashMap<>();
    private final Map<Path, DocumentNode> classDocuments = new HashMap<>();
    private final Map<String, List<ClassInfo>> generatedClasses = new HashMap<>();

    public Compiler(Logger logger) {
        this.logger = logger;
    }

    /**
     * First step of FXML compilation:
     * Parse all FXML files in the specified source directory and generate supporting Java source files.
     *
     * @param sourceDir the source directory
     * @param classpath the compile classpath
     */
    @SuppressWarnings("unused")
    public void preprocessFiles(File sourceDir, File generatedSourcesBaseDir, Set<File> classpath) throws IOException {
        Transformer transformer = Transformer.getCodeTransformer(newClassPool(classpath));
        Path baseDir = sourceDir.toPath();

        for (Path sourceFile : FileUtil.enumerateFiles(baseDir, this::fileFilter)) {
            var context = new CompilationContext(new CompilationSource.FileSystem(sourceFile));

            try (var ignored = new CompilationScope(context)) {
                DocumentNode document = new FxmlParser(baseDir, sourceFile).parseDocument();
                compilations.put(sourceFile, new Compilation(document, context));
                parseSingleFile(sourceFile, document, transformer, generatedClasses);
            } catch (FxmlParseAbortException ex) {
                skippedFiles.add(sourceFile);
                logger.info("File skipped: " + sourceFile.toString());
            } catch (MarkupException ex) {
                ex.setSourceFile(sourceFile.toFile());
                throw ex;
            }
        }

        generateSources(generatedSourcesBaseDir);
    }

    /**
     * Second step in FXML compilation:
     * Compiles all FXML files in the specified source directory.
     *
     * @param sourceDir the source directory
     * @param classpath the compile classpath
     */
    @SuppressWarnings("unused")
    public void compileFiles(File sourceDir, Set<File> classpath) throws IOException {
        Transformer transformer = null;

        for (Path inputPath : FileUtil.enumerateFiles(sourceDir.toPath(), this::fileFilter)) {
            if (skippedFiles.contains(inputPath)) {
                continue;
            }

            if (transformer == null) {
                transformer = Transformer.getBytecodeTransformer(newClassPool(classpath));
            }

            compileSingleFile(inputPath, transformer);
        }
    }

    private void generateSources(File generatedSourcesBasePath) throws IOException {
        for (var entry : generatedClasses.entrySet()) {
            String packageName = entry.getKey();
            Path outputDir = generatedSourcesBasePath.toPath().resolve(packageName.replace(".", "/"));
            deleteFiles(outputDir);
            writeClasses(outputDir, entry.getValue());
            writeAccessorClass(outputDir, packageName, entry.getValue());
        }

        generatedClasses.clear();
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

    private void parseSingleFile(
            Path inputFile,
            DocumentNode document,
            Transformer transformer,
            Map<String, List<ClassInfo>> generatedClasses) {
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
        generatedClasses.get(packageName).add(new ClassInfo(classNode, stringBuilder.toString()));
    }

    private void writeClasses(Path outputDir, List<ClassInfo> classes) throws IOException {
        Files.createDirectories(outputDir);

        for (ClassInfo classInfo : classes) {
            Files.writeString(
                outputDir.resolve(
                    classInfo.classNode().hasCodeBehind() ?
                        classInfo.classNode().getMangledClassName() + ".java" :
                        classInfo.classNode().getClassName() + ".java"),
                classInfo.source(),
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
                    "\t%sstatic abstract class %s extends %s {",
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

            stringBuilder.append("}\r\n");
        }

        stringBuilder.append("}\r\n");

        Files.writeString(
            outputDir.resolve("markup.java"),
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
        Compilation compilation = compilations.get(inputFile);

        try (var ignored = new CompilationScope(compilation.context())) {
            ClassNode classNode = (ClassNode)classDocuments.get(inputFile).getRoot();
            boolean hasCodeBehind = classNode.hasCodeBehind();
            String packageName = classNode.getPackageName();
            String codeBehindClassName = packageName + "." + classNode.getClassName();
            String markupClassName =
                packageName + "." + (hasCodeBehind ? classNode.getMangledClassName() : classNode.getClassName());
            URL classUrl = transformer.getClassPool().find(markupClassName);

            if (classUrl == null) {
                throw new RuntimeException(String.format("%s cannot be found on the classpath", markupClassName));
            }

            CtClass codeBehindClass = transformer.getClassPool().get(codeBehindClassName);
            CtClass markupClass = transformer.getClassPool().get(markupClassName);
            markupClass.defrost();

            Bytecode bytecode = new Bytecode(markupClass, 1);

            EmitInitializeRootNode rootNode = (EmitInitializeRootNode)transformer.transform(
                compilations.get(inputFile).document, codeBehindClass, markupClass);

            BytecodeEmitContext emitContext = new BytecodeEmitContext(
                codeBehindClass, markupClass, rootNode, compilations.get(inputFile).document.getImports(), bytecode);

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
            MarkupException m = GeneralErrors.internalError(ex.getMessage());
            m.setSourceFile(inputFile.toFile());
            throw m;
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

    private record ClassInfo(ClassNode classNode, String source) {}
    private record Compilation(DocumentNode document, CompilationContext context) {}

}
