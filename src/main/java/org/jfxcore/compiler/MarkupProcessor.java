// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.LineMap;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import org.jfxcore.compiler.diagnostic.Location;
import org.jfxcore.compiler.diagnostic.Logger;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.util.CompilationUnit;
import org.jfxcore.compiler.util.CompilationUnitDescriptor;
import org.jfxcore.compiler.util.QualifiedName;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.io.Writer;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@SupportedAnnotationTypes(MarkupProcessor.MARKUP_ANNOTATION_NAME)
@SupportedOptions({ProcessorOptions.SOURCE_DIRS_OPT,
                   ProcessorOptions.SEARCH_PATH_OPT,
                   ProcessorOptions.INTERMEDIATE_BUILD_DIR_OPT})
public final class MarkupProcessor extends AbstractProcessor {

    static final String MARKUP_ANNOTATION_NAME = "org.jfxcore.markup.ComponentView";

    private final Map<Path, AnnotationInfo> sourceFiles = new HashMap<>();
    private final Set<String> processedOwners = new HashSet<>();

    private Elements elements;
    private Types types;
    private Filer filer;
    private Messager messager;
    private Trees trees;
    private ProcessorOptions options;
    private ClassGenerator generator;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.elements = processingEnv.getElementUtils();
        this.types = processingEnv.getTypeUtils();
        this.filer = processingEnv.getFiler();
        this.messager = processingEnv.getMessager();
        this.trees = Trees.instance(processingEnv);
        this.options = ProcessorOptions.parse(processingEnv);
        this.generator = new ClassGenerator(options.searchPath(), new CompilerLogger(messager));
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            return false;
        }

        TypeElement annotationType = elements.getTypeElement(MARKUP_ANNOTATION_NAME);
        if (annotationType == null) {
            return false;
        }

        for (Element element : roundEnv.getElementsAnnotatedWith(annotationType)) {
            if (element instanceof TypeElement typeElement) {
                processSingleElement(typeElement);
            }
        }

        try {
            for (CompilationUnit compilationUnit : generator.process()) {
                CompilationUnitDescriptor descriptor = compilationUnit.descriptor();
                String fileName = descriptor.markupClass().fullName();
                TypeElement originatingElement = sourceFiles.get(descriptor.absoluteSourceFile()).element();
                String superClassName = getSuperClassName(originatingElement);

                if (!descriptor.markupClass().simpleName().equals(superClassName)) {
                    error(originatingElement, null, null, String.format(
                        "Annotated class %s must extend %s",
                        originatingElement.getSimpleName(),
                        compilationUnit.descriptor().markupClass().simpleName()));
                }

                try (Writer writer = filer.createSourceFile(fileName, originatingElement).openWriter()) {
                    writer.write(compilationUnit.generatedSourceText());
                }

                descriptor.writeTo(options.intermediateBuildDir());
            }
        } catch (MarkupException ex) {
            AnnotationInfo annoInfo = sourceFiles.get(ex.getSourceFile().toPath());
            error(annoInfo.element(), annoInfo.annotation(), annoInfo.value(), ex.getMessageWithSourceInfo());
        } catch (IOException ex) {
            error(null, null, null, ex.getMessage());
        }

        return true;
    }

    private void processSingleElement(TypeElement typeElement) {
        if (!processedOwners.add(typeElement.getQualifiedName().toString())) {
            return;
        }

        if (typeElement.getKind() != ElementKind.CLASS) {
            error(typeElement, null, null, String.format(
                "@%s can only be used on classes", getSimpleName(MARKUP_ANNOTATION_NAME)));
            return;
        }

        if (typeElement.getNestingKind() != NestingKind.TOP_LEVEL) {
            error(typeElement, null, null, String.format(
                "@%s can only be used on top-level classes", getSimpleName(MARKUP_ANNOTATION_NAME)));
            return;
        }

        if (typeElement.getModifiers().contains(Modifier.PRIVATE)) {
            error(typeElement, null, null, String.format(
                "Class annotated with @%s must not be private", getSimpleName(MARKUP_ANNOTATION_NAME)));
            return;
        }

        AnnotationMirror annotation = getAnnotationMirror(typeElement, MARKUP_ANNOTATION_NAME);

        try {
            processAnnotatedClass(typeElement, annotation);
        } catch (Exception ex) {
            AnnotationValue markupValue = getAnnotationValue(annotation);
            error(typeElement, annotation, markupValue, ex.getMessage());
        }
    }

    private void processAnnotatedClass(TypeElement typeElement, AnnotationMirror annotation) {
        AnnotationValue markupValue = getAnnotationValue(annotation);
        String markupText = (String)markupValue.getValue();

        if (markupText == null || markupText.isBlank()) {
            throw new IllegalArgumentException(String.format(
                "@%s value must not be empty", getSimpleName(MARKUP_ANNOTATION_NAME)));
        }

        Path sourceFile = getSourceFile(typeElement, annotation);
        if (sourceFile == null) {
            return;
        }

        Path sourceDir = options.sourceDirs().stream()
            .filter(sourceFile::startsWith)
            .findFirst()
            .orElse(null);

        if (sourceDir == null) {
            throw new IllegalArgumentException(
                "Annotated source file is not contained in one of the recognized source directories: "
                + options.sourceDirs());
        }

        QualifiedName sourceClassName = QualifiedName.of(typeElement.getQualifiedName().toString());
        Location sourceOffset = getSourceOffset(typeElement, annotation, markupValue);
        List<String> imports = getImports(typeElement);

        try {
            generator.addEmbeddedSource(sourceDir, sourceFile, markupText, imports, sourceClassName, sourceOffset);
            sourceFiles.put(sourceFile, new AnnotationInfo(typeElement, annotation, markupValue));
        } catch (MarkupException ex) {
            error(typeElement, annotation, markupValue, ex.getMessageWithSourceInfo());
        }
    }

    private String getSuperClassName(TypeElement typeElement) {
        var superclass = typeElement.getSuperclass();
        if (superclass.getKind() == TypeKind.NONE) {
            return null;
        }

        Element superElement = types.asElement(superclass);
        if (!(superElement instanceof TypeElement superTypeElement)) {
            return null;
        }

        return superTypeElement.getSimpleName().toString();
    }

    private List<String> getImports(TypeElement typeElement) {
        TreePath path = trees.getPath(typeElement);
        if (path == null) {
            return List.of();
        }

        CompilationUnitTree unit = path.getCompilationUnit();
        if (unit == null) {
            return List.of();
        }

        List<String> imports = new ArrayList<>();

        for (ImportTree importTree : unit.getImports()) {
            String qualifiedImport = importTree.getQualifiedIdentifier().toString();

            if (!importTree.isStatic() && !MARKUP_ANNOTATION_NAME.equals(qualifiedImport)) {
                imports.add(qualifiedImport);
            }
        }

        return imports;
    }

    private Path getSourceFile(TypeElement typeElement, AnnotationMirror annotationMirror) {
        TreePath path = trees.getPath(typeElement);
        if (path == null) {
            return null;
        }

        CompilationUnitTree unit = path.getCompilationUnit();
        if (unit == null || unit.getSourceFile() == null) {
            return null;
        }

        URI uri = unit.getSourceFile().toUri();
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            try {
                return Paths.get(uri).normalize();
            } catch (Exception ex) {
                error(typeElement, annotationMirror, null, "Invalid source file path: " + uri);
            }
        } else {
            error(typeElement, annotationMirror, null, "Annotated class is not a source file");
        }

        return null;
    }

    private Location getSourceOffset(TypeElement typeElement, AnnotationMirror annotation, AnnotationValue value) {
        TreePath valuePath = trees.getPath(typeElement, annotation, value);
        if (valuePath == null) {
            return new Location(0, 0);
        }

        SourcePositions sourcePositions = trees.getSourcePositions();
        CompilationUnitTree compilationUnit = valuePath.getCompilationUnit();
        long position = sourcePositions.getStartPosition(compilationUnit, valuePath.getLeaf());
        if (position == Diagnostic.NOPOS) {
            return new Location(0, 0);
        }

        // Line and column numbers in the AST available to the annotation processor are 1-based, but the
        // FXML compiler expects source locations to be 0-based. However, in almost all cases, embedded
        // markup will be specified with a text block literal. In this case, the actual text content
        // begins on the next line after the """ delimiter, so we add one line to get the first line of
        // the embedded markup.
        LineMap lineMap = compilationUnit.getLineMap();
        long line = lineMap.getLineNumber(position);
        long column  = lineMap.getColumnNumber(position) - 1;
        return new Location((int)line, (int)column);
    }

    private AnnotationMirror getAnnotationMirror(TypeElement type, String name) {
        for (AnnotationMirror mirror : type.getAnnotationMirrors()) {
            Element annotationElement = mirror.getAnnotationType().asElement();

            if (annotationElement instanceof TypeElement annotationType
                    && annotationType.getQualifiedName().contentEquals(name)) {
                return mirror;
            }
        }

        throw new RuntimeException("Annotation not found: " + name);
    }

    private AnnotationValue getAnnotationValue(AnnotationMirror annotation) {
        for (var entry : elements.getElementValuesWithDefaults(annotation).entrySet()) {
            if (entry.getKey().getSimpleName().contentEquals("value")) {
                return entry.getValue();
            }
        }

        throw new RuntimeException("Annotation element not found: value");
    }

    private String getSimpleName(String name) {
        return name.substring(name.lastIndexOf('.') + 1);
    }

    private void error(Element element, AnnotationMirror annotation, AnnotationValue value, String message) {
        if (element != null && annotation != null && value != null) {
            messager.printMessage(Diagnostic.Kind.ERROR, message, element, annotation, value);
        } else if (element != null && annotation != null) {
            messager.printMessage(Diagnostic.Kind.ERROR, message, element, annotation);
        } else if (element != null) {
            messager.printMessage(Diagnostic.Kind.ERROR, message, element);
        } else {
            messager.printMessage(Diagnostic.Kind.ERROR, message);
        }
    }

    private record CompilerLogger(Messager messager) implements Logger {
        @Override
        public void fine(String message) {
        }

        @Override
        public void info(String message) {
            messager.printMessage(Diagnostic.Kind.NOTE, message);
        }
    }

    private record AnnotationInfo(TypeElement element, AnnotationMirror annotation, AnnotationValue value) {}
}
