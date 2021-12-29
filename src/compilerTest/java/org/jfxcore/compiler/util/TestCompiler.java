// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.util;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.bytecode.MethodInfo;
import org.jfxcore.compiler.ast.DocumentNode;
import org.jfxcore.compiler.ast.codebehind.ClassNode;
import org.jfxcore.compiler.ast.codebehind.JavaEmitContext;
import org.jfxcore.compiler.ast.emit.BytecodeEmitContext;
import org.jfxcore.compiler.ast.emit.EmitInitializeRootNode;
import org.jfxcore.compiler.diagnostic.ErrorCode;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.parse.FxmlParser;
import org.jfxcore.compiler.transform.Transformer;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TestCompiler extends AbstractCompiler {

    @SuppressWarnings("unchecked")
    public static <T> T newInstance(String fileName, String fxml) {
        try {
            Constructor<?> ctor = new TestCompiler().compileClass(fileName, fxml).getDeclaredConstructor();
            ctor.setAccessible(true);
            return (T)ctor.newInstance();
        } catch (InvocationTargetException ex) {
            if (ex.getCause() instanceof RuntimeException) {
                throw (RuntimeException)ex.getCause();
            }

            throw new RuntimeException(ex);
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Throwable ex) {
            throw new RuntimeException(ex);
        }
    }

    @SuppressWarnings("unchecked")
    public <T> Class<T> compileClass(String fileName, String source) {
        var classPool = new ClassPool();
        classPool.appendSystemPath();

        ClassNode classNode;
        StringBuilder codeBehind = new StringBuilder();
        DocumentNode document;
        String simpleClassName;

        Path sourceBaseDir = Path.of("").toAbsolutePath().resolve("src/compilerTest/java");
        Path fxmlTestSourcePath = Path.of("").toAbsolutePath()
            .resolve("src/compilerTest/java/org/jfxcore/compiler/classes/" + fileName + ".fxml");

        CompilationContext context = new CompilationContext(new CompilationSource.InMemory(source));

        try (CompilationScope ignored = new CompilationScope(context)) {
            document = new FxmlParser(sourceBaseDir, fxmlTestSourcePath, source).parseDocument();

            DocumentNode codeDocument = (DocumentNode)Transformer.getCodeTransformer(classPool)
                .transform(document, null, null);
            new JavaEmitContext(codeBehind).emit(codeDocument);

            classNode = (ClassNode)codeDocument.getRoot();
            simpleClassName = classNode.hasCodeBehind() ? classNode.getMangledClassName() : classNode.getClassName();
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }

        Path buildBaseDir = Path.of("").toAbsolutePath();
        Path classDir = buildBaseDir.resolve("build/classes/java/compilerTest");
        Path sourceFile = buildBaseDir
            .resolve("build/generated/java/compilerTest/java/" + classNode.getPackageName().replace(".", "/"))
            .resolve(simpleClassName + ".java");

        try {
            Files.createDirectories(sourceFile.getParent());
            Files.writeString(
                sourceFile,
                codeBehind.toString(),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        javax.tools.JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);
        List<Diagnostic<? extends JavaFileObject>> diagnostics = new ArrayList<>();

        boolean ret = compiler
            .getTask(
                null,
                null,
                diagnostics::add,
                Arrays.asList(
                    "-d", classDir.toString(),
                    "-classpath", System.getProperty("java.class.path") + File.pathSeparator + classDir,
                    "--module-path", System.getProperty("java.class.path"),
                    "--add-modules", "javafx.base,javafx.graphics,javafx.controls,javafx.fxml",
                    "--release", System.getProperty("java.specification.version")),
                null,
                fileManager.getJavaFileObjects(sourceFile.toString()))
            .call();

        if (!ret) {
            var file = diagnostics.get(0).getSource();
            if (file != null) {
                file.delete();
            }

            throw new RuntimeException(diagnostics.get(0).getCode());
        }

        try (CompilationScope ignored = new CompilationScope(context)) {
            String packageName = classNode.getPackageName();
            String className = packageName + "." + simpleClassName;
            EmitInitializeRootNode transformedNode = (EmitInitializeRootNode)Transformer.getBytecodeTransformer(classPool)
                .transform(document, classPool.get(className), classPool.get(className));

            URL classUrl = classPool.find(className);

            if (classUrl == null) {
                throw new RuntimeException(String.format("%s can not be found on the classpath", className));
            }

            CtClass generatedClass = classPool.get(className);
            generatedClass.defrost();

            Bytecode bytecode = new Bytecode(generatedClass, 1);
            BytecodeEmitContext bytecodeContext = new BytecodeEmitContext(
                generatedClass, generatedClass, transformedNode, document.getImports(), bytecode);
            bytecodeContext.emitRootNode();

            MethodInfo methodInfo = generatedClass.getClassFile().getMethod("initializeComponent");
            methodInfo.setCodeAttribute(bytecode.toCodeAttribute());
            methodInfo.rebuildStackMap(generatedClass.getClassPool());

            flushModifiedClasses(context);

            int packages = packageName.length() - packageName.replace(".", "").length() + 1;
            Path classFile = Paths.get(classUrl.toURI());
            Path outDir = FileUtil.removeLastN(classFile, packages + 1);

            generatedClass.writeFile(outDir.toString());

            for (CtClass nestedClass : bytecodeContext.getNestedClasses()) {
                nestedClass.writeFile(outDir.toString());
            }

            return (Class<T>)Class.forName(generatedClass.getName());
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Throwable ex) {
            throw new RuntimeException(ex);
        }
    }

}
