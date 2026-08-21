// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.util;

import org.jfxcore.compiler.diagnostic.Location;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.diagnostic.errors.ResourceErrors;
import org.jfxcore.compiler.resource.EmbeddedResource;
import org.jfxcore.compiler.type.TypeDeclaration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

public final class CompilerOutputTracker {

    private static final Comparator<Artifact> ARTIFACT_ORDER = Comparator
        .comparing(Artifact::path, String.CASE_INSENSITIVE_ORDER)
        .thenComparing(Artifact::path)
        .thenComparing(Artifact::description);

    private final Map<String, Artifact> namespace = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    public void registerResource(EmbeddedResource resource) {
        registerResource(resource, null);
    }

    public void registerResource(EmbeddedResource resource, CompilationUnitDescriptor owner) {
        String path = validateLogicalPath(resource.logicalPath());
        Artifact incoming = Artifact.resource(path, resource, owner);
        Artifact existing = namespace.get(path);

        if (existing != null) {
            if (existing.sameDeclaration(incoming)) {
                return;
            }

            throw collision(existing, incoming);
        }

        namespace.put(path, incoming);
    }

    public void registerClass(TypeDeclaration classDecl) {
        String logicalPath = classDecl.name().replace('.', '/') + ".class";
        registerClass(logicalPath, classDecl.name());
    }

    public void registerClass(QualifiedName name) {
        String fullName = name.fullName();
        String path = fullName.replace('.', '/') + ".class";
        registerClass(path, fullName);
    }

    public void registerClass(String logicalPath, String name) {
        String path = validateLogicalPath(logicalPath);
        Artifact incoming = Artifact.generatedClass(path, name);
        Artifact existing = namespace.get(path);

        if (existing != null) {
            if (existing.description().equals(incoming.description())
                    && existing.path().equals(incoming.path())) {
                return;
            }

            throw collision(existing, incoming);
        }

        namespace.put(path, incoming);
    }

    private String validateLogicalPath(String path) {
        if (path == null || path.isEmpty() || path.startsWith("/") || path.contains("\\")) {
            throw new IllegalArgumentException("Invalid compiler output path: " + path);
        }

        for (String component : path.split("/", -1)) {
            if (component.isEmpty() || component.equals(".") || component.equals("..")) {
                throw new IllegalArgumentException("Invalid compiler output path: " + path);
            }
        }

        return path;
    }

    private MarkupException collision(Artifact first, Artifact second) {
        List<Artifact> ordered = Stream.of(first, second).sorted(ARTIFACT_ORDER).toList();
        Artifact firstArtifact = ordered.get(0);
        Artifact secondArtifact = ordered.get(1);
        Artifact resourceArtifact = firstArtifact.resource() != null ? firstArtifact : secondArtifact;
        EmbeddedResource resource = resourceArtifact.resource();

        MarkupException exception = ResourceErrors.resourceFileCollision(
            resource != null ? resource.declarationSourceInfo() : SourceInfo.none(),
            firstArtifact.path(),
            firstArtifact.description(),
            secondArtifact.description());

        if (resourceArtifact.owner() != null) {
            CompilationUnitDescriptor owner = resourceArtifact.owner();
            exception.setSourceFile(owner.absoluteSourceFile().toAbsolutePath().normalize().toFile());
            exception.setSourceOffset(owner.embeddingContext() != null
                ? owner.embeddingContext().sourceOffset()
                : new Location(0, 0));
        }

        return exception;
    }

    private record Artifact(
            String path,
            String description,
            EmbeddedResource resource,
            CompilationUnitDescriptor owner) {

        private static Artifact resource(String path, EmbeddedResource resource, CompilationUnitDescriptor owner) {
            SourceInfo declaration = resource.declarationSourceInfo().toOriginal();
            String description = "%s resource '%s' declared at %s".formatted(
                resource.declaringSource(), resource.logicalName(), declaration.getStart());

            return new Artifact(path, description, resource, owner);
        }

        private static Artifact generatedClass(String path, String className) {
            return new Artifact(path, "generated class '" + className + "'", null, null);
        }

        private boolean sameDeclaration(Artifact other) {
            return resource != null
                && resource == other.resource
                && path.equals(other.path);
        }
    }
}
