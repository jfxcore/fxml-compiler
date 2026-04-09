// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler;

import com.google.devtools.ksp.processing.CodeGenerator;
import com.google.devtools.ksp.processing.Dependencies;
import com.google.devtools.ksp.processing.KSPLogger;
import com.google.devtools.ksp.processing.Resolver;
import com.google.devtools.ksp.processing.SymbolProcessor;
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment;
import com.google.devtools.ksp.symbol.ClassKind;
import com.google.devtools.ksp.symbol.FileLocation;
import com.google.devtools.ksp.symbol.KSAnnotated;
import com.google.devtools.ksp.symbol.KSAnnotation;
import com.google.devtools.ksp.symbol.KSClassDeclaration;
import com.google.devtools.ksp.symbol.KSClassifierReference;
import com.google.devtools.ksp.symbol.KSDeclaration;
import com.google.devtools.ksp.symbol.KSFile;
import com.google.devtools.ksp.symbol.KSName;
import com.google.devtools.ksp.symbol.KSNode;
import com.google.devtools.ksp.symbol.KSTypeReference;
import com.google.devtools.ksp.symbol.KSValueArgument;
import com.google.devtools.ksp.symbol.Modifier;
import org.jetbrains.annotations.NotNull;
import org.jfxcore.compiler.diagnostic.Location;
import org.jfxcore.compiler.diagnostic.Logger;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.util.CompilationUnit;
import org.jfxcore.compiler.util.CompilationUnitDescriptor;
import org.jfxcore.compiler.util.QualifiedName;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class MarkupSymbolProcessor implements SymbolProcessor {

    private static final String MARKUP_ANNOTATION_NAME = "org.jfxcore.markup.ComponentView";
    private static final Pattern IMPORT_PATTERN = Pattern.compile(
        "^\\s*import\\s+([^\\s;]+?)(?:\\s+as\\s+([A-Za-z_][A-Za-z0-9_]*))?\\s*;?\\s*$");

    private final CodeGenerator codeGenerator;
    private final KSPLogger logger;
    private final ProcessorOptions options;
    private final ClassGenerator generator;

    private final Map<Path, AnnotationInfo> sourceFiles = new HashMap<>();
    private final Set<String> processedOwners = new HashSet<>();

    MarkupSymbolProcessor(SymbolProcessorEnvironment environment) {
        this.codeGenerator = environment.getCodeGenerator();
        this.logger = environment.getLogger();
        this.options = ProcessorOptions.parse(environment.getOptions());
        this.generator = new ClassGenerator(options.searchPath(), new CompilerLogger(logger));
    }

    @Override
    public @NotNull List<KSAnnotated> process(@NotNull Resolver resolver) {
        Iterator<KSAnnotated> symbols = resolver.getSymbolsWithAnnotation(MARKUP_ANNOTATION_NAME, false).iterator();
        while (symbols.hasNext()) {
            KSAnnotated symbol = symbols.next();
            if (symbol instanceof KSClassDeclaration declaration) {
                processSingleElement(declaration, resolver);
            } else {
                error(symbol, null, null, String.format(
                    "@%s can only be used on classes", getSimpleName(MARKUP_ANNOTATION_NAME)));
            }
        }

        emitGeneratedSources();
        return List.of();
    }

    private void emitGeneratedSources() {
        try {
            for (CompilationUnit compilationUnit : generator.process()) {
                CompilationUnitDescriptor descriptor = compilationUnit.descriptor();
                AnnotationInfo info = sourceFiles.get(descriptor.absoluteSourceFile());
                QualifiedName generatedClass = descriptor.markupClass();
                String packageName = generatedClass.packageName().fullName();
                String superClassName = info != null ? getSuperClassName(info.element()) : null;
                Dependencies dependencies = info != null && info.element().getContainingFile() != null
                    ? new Dependencies(false, info.element().getContainingFile())
                    : new Dependencies(false);

                if (info != null && !generatedClass.simpleName().equals(superClassName)) {
                    error(info.element(), null, null, String.format(
                        "Annotated class %s must extend %s",
                        info.element().getSimpleName().asString(),
                        generatedClass.simpleName()));
                }

                try (Writer writer = new OutputStreamWriter(
                        codeGenerator.createNewFile(dependencies, packageName, generatedClass.simpleName(), "java"),
                        StandardCharsets.UTF_8)) {
                    writer.write(compilationUnit.generatedSourceText());
                }

                descriptor.writeTo(options.intermediateBuildDir());
            }
        } catch (MarkupException ex) {
            AnnotationInfo info = sourceFiles.get(ex.getSourceFile().toPath());
            if (info != null) {
                error(info.element(), info.annotation(), info.value(), ex.getMessageWithSourceInfo());
            } else {
                error(null, null, null, ex.getMessageWithSourceInfo());
            }
        } catch (IOException ex) {
            error(null, null, null, ex.getMessage());
        }
    }

    private void processSingleElement(KSClassDeclaration declaration, Resolver resolver) {
        KSName qualifiedName = declaration.getQualifiedName();
        String ownerName = qualifiedName != null
            ? qualifiedName.asString()
            : declaration.getSimpleName().asString();

        if (!processedOwners.add(ownerName)) {
            return;
        }

        if (declaration.getClassKind() != ClassKind.CLASS) {
            error(declaration, null, null, String.format(
                "@%s can only be used on classes", getSimpleName(MARKUP_ANNOTATION_NAME)));
            return;
        }

        if (declaration.getParentDeclaration() != null) {
            error(declaration, null, null, String.format(
                "@%s can only be used on top-level classes", getSimpleName(MARKUP_ANNOTATION_NAME)));
            return;
        }

        if (declaration.getModifiers().contains(Modifier.PRIVATE)) {
            error(declaration, null, null, String.format(
                "Class annotated with @%s must not be private", getSimpleName(MARKUP_ANNOTATION_NAME)));
            return;
        }

        KSAnnotation annotation = getAnnotation(declaration);

        try {
            processAnnotatedClass(declaration, annotation, resolver);
        } catch (Exception ex) {
            KSValueArgument markupValue = getAnnotationValue(annotation);
            error(declaration, annotation, markupValue, ex.getMessage());
        }
    }

    private void processAnnotatedClass(KSClassDeclaration declaration, KSAnnotation annotation, Resolver resolver) {
        KSValueArgument markupValue = getAnnotationValue(annotation);
        Object markupValueObject = markupValue.getValue();
        String markupText = markupValueObject instanceof String text ? text : null;

        if (markupText == null || markupText.isBlank()) {
            throw new IllegalArgumentException(String.format(
                "@%s value must not be empty", getSimpleName(MARKUP_ANNOTATION_NAME)));
        }

        KSFile containingFile = declaration.getContainingFile();
        if (containingFile == null) {
            throw new IllegalArgumentException(String.format(
                "@%s can only be used in a source file", getSimpleName(MARKUP_ANNOTATION_NAME)));
        }

        Path sourceFile = getSourceFile(containingFile);
        Path sourceDir = options.sourceDirs().stream()
            .filter(sourceFile::startsWith)
            .findFirst()
            .orElse(null);

        if (sourceDir == null) {
            throw new IllegalArgumentException(
                "Annotated source file is not contained in one of the recognized source directories: "
                + options.sourceDirs());
        }

        KSName qualifiedName = Objects.requireNonNull(declaration.getQualifiedName());
        QualifiedName sourceClassName = QualifiedName.of(qualifiedName.asString());
        Location sourceOffset = getSourceOffset(annotation, markupValue);
        List<String> imports = getImports(containingFile, annotation, resolver);

        try {
            generator.addEmbeddedSource(sourceDir, sourceFile, markupText, imports, sourceClassName, sourceOffset);
            sourceFiles.put(sourceFile, new AnnotationInfo(declaration, annotation, markupValue));
        } catch (MarkupException ex) {
            error(declaration, annotation, markupValue, ex.getMessageWithSourceInfo());
        }
    }

    private String getSuperClassName(KSClassDeclaration declaration) {
        Iterator<KSTypeReference> it = declaration.getSuperTypes().iterator();
        while (it.hasNext()) {
            KSTypeReference superType = it.next();
            var resolvedType = superType.resolve();
            if (resolvedType.isError()) {
                String referencedName = getReferencedSuperClassName(superType);
                if (referencedName != null) {
                    return referencedName;
                }

                continue;
            }

            KSDeclaration superDeclaration = resolvedType.getDeclaration();

            if (superDeclaration instanceof KSClassDeclaration superClass
                    && superClass.getClassKind() == ClassKind.CLASS) {
                KSName qualifiedName = superClass.getQualifiedName();
                if (qualifiedName == null || !"kotlin.Any".equals(qualifiedName.asString())) {
                    return superClass.getSimpleName().asString();
                }
            }
        }

        return null;
    }

    private String getReferencedSuperClassName(KSTypeReference superType) {
        if (superType.getElement() instanceof KSClassifierReference classifierReference) {
            String referencedName = classifierReference.referencedName();
            if (!referencedName.isBlank() && !"Any".equals(referencedName)) {
                return referencedName;
            }
        }

        return null;
    }

    private List<String> getImports(KSFile containingFile, KSNode annotation, Resolver resolver) {
        Path sourceFile = getSourceFile(containingFile);
        Set<String> imports = new LinkedHashSet<>();

        try {
            for (String line : Files.readAllLines(sourceFile, StandardCharsets.UTF_8)) {
                Matcher matcher = IMPORT_PATTERN.matcher(line);
                if (!matcher.matches()) {
                    continue;
                }

                String rawImport = matcher.group(1);
                String qualifiedImport = normalizeImport(rawImport);
                String alias = matcher.group(2);

                if (alias != null) {
                    logger.info(String.format(
                        "Ignoring aliased Kotlin import '%s' for @%s processing", rawImport,
                        getSimpleName(MARKUP_ANNOTATION_NAME)), annotation);
                } else if (!MARKUP_ANNOTATION_NAME.equals(qualifiedImport)
                        && isJavaCompatibleImport(resolver, qualifiedImport)) {
                    imports.add(qualifiedImport);
                }
            }
        } catch (IOException ex) {
            throw new IllegalArgumentException("Could not read source file: " + sourceFile);
        }

        return List.copyOf(imports);
    }

    private boolean isJavaCompatibleImport(Resolver resolver, String qualifiedImport) {
        return qualifiedImport.endsWith(".*") || resolvesToClass(resolver, qualifiedImport);
    }

    static String normalizeImport(String qualifiedImport) {
        return Arrays.stream(qualifiedImport.split("\\."))
            .map(MarkupSymbolProcessor::stripBackticks)
            .reduce((left, right) -> left + "." + right)
            .orElse(qualifiedImport);
    }

    static boolean resolvesToClass(Resolver resolver, String qualifiedName) {
        if (resolver.getClassDeclarationByName(resolver.getKSNameFromString(qualifiedName)) != null) {
            return true;
        }

        String[] parts = qualifiedName.split("\\.");

        for (int split = parts.length - 1; split > 0; --split) {
            String enclosingName = String.join(".", Arrays.copyOf(parts, split));
            KSClassDeclaration enclosingClass = resolver.getClassDeclarationByName(
                resolver.getKSNameFromString(enclosingName));

            if (enclosingClass != null && resolvesNestedClass(
                    enclosingClass, Arrays.copyOfRange(parts, split, parts.length))) {
                return true;
            }
        }

        return false;
    }

    private static boolean resolvesNestedClass(KSClassDeclaration enclosingClass, String[] nestedNames) {
        KSClassDeclaration currentClass = enclosingClass;

        for (String nestedName : nestedNames) {
            currentClass = findNestedClass(currentClass, nestedName);
            if (currentClass == null) {
                return false;
            }
        }

        return true;
    }

    private static KSClassDeclaration findNestedClass(KSClassDeclaration enclosingClass, String nestedName) {
        Iterator<KSDeclaration> it = enclosingClass.getDeclarations().iterator();
        while (it.hasNext()) {
            if (it.next() instanceof KSClassDeclaration nestedClass
                    && nestedClass.getSimpleName().asString().equals(nestedName)) {
                return nestedClass;
            }
        }

        return null;
    }

    private static String stripBackticks(String identifier) {
        return identifier.length() >= 2
            && identifier.charAt(0) == '`'
            && identifier.charAt(identifier.length() - 1) == '`'
                ? identifier.substring(1, identifier.length() - 1)
                : identifier;
    }

    private Path getSourceFile(KSFile containingFile) {
        return Path.of(containingFile.getFilePath()).normalize();
    }

    private Location getSourceOffset(KSAnnotation annotation, KSValueArgument value) {
        int line = getLineNumber(value);
        if (line < 0) {
            line = getLineNumber(annotation);
        }

        return new Location(Math.max(0, line), 0);
    }

    private int getLineNumber(KSNode node) {
        return node != null
            ? node.getLocation() instanceof FileLocation fileLocation
                ? fileLocation.getLineNumber() - 1
                : -1
            : -1;
    }

    private KSAnnotation getAnnotation(KSClassDeclaration declaration) {
        Iterator<KSAnnotation> it = declaration.getAnnotations().iterator();
        while (it.hasNext()) {
            KSAnnotation annotation = it.next();
            KSDeclaration annotationDeclaration = annotation.getAnnotationType().resolve().getDeclaration();
            KSName qualifiedName = annotationDeclaration.getQualifiedName();
            if (qualifiedName != null && MARKUP_ANNOTATION_NAME.equals(qualifiedName.asString())) {
                return annotation;
            }
        }

        throw new IllegalStateException("Annotation not found: " + MARKUP_ANNOTATION_NAME);
    }

    private KSValueArgument getAnnotationValue(KSAnnotation annotation) {
        for (KSValueArgument argument : annotation.getArguments()) {
            KSName name = argument.getName();
            if (name == null || name.asString().contentEquals("value")) {
                return argument;
            }
        }

        throw new IllegalStateException("Annotation element not found: value");
    }

    private String getSimpleName(String name) {
        return name.substring(name.lastIndexOf('.') + 1);
    }

    private void error(KSNode element, KSNode annotation, KSNode value, String message) {
        KSNode node = value != null ? value : annotation != null ? annotation : element;
        logger.error(message, node);
    }

    private record CompilerLogger(KSPLogger logger) implements Logger {
        @Override
        public void fine(String message) {
        }

        @Override
        public void info(String message) {
            logger.info(message, null);
        }
    }

    private record AnnotationInfo(KSClassDeclaration element, KSAnnotation annotation, KSValueArgument value) {}
}
