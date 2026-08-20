// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.resource;

import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.diagnostic.errors.ResourceErrors;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

public final class EmbeddedResourceCollector {

    private static final Comparator<Artifact> ARTIFACT_ORDER = Comparator
        .comparing(Artifact::path, String.CASE_INSENSITIVE_ORDER)
        .thenComparing(Artifact::path)
        .thenComparing(Artifact::description);

    private final Map<String, Artifact> namespace = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private final Map<String, EmbeddedResource> resources = new LinkedHashMap<>();

    public void request(EmbeddedResource resource) {
        String path = validateLogicalPath(resource.logicalPath());
        Artifact incoming = Artifact.resource(path, resource);
        Artifact existing = namespace.get(path);

        if (existing != null) {
            if (existing.sameDeclaration(incoming)) {
                return;
            }

            throw collision(existing, incoming);
        }

        namespace.put(path, incoming);
        resources.put(path, resource);
    }

    public void reserveClass(String logicalPath, String className) {
        String path = validateLogicalPath(logicalPath);
        Artifact incoming = Artifact.generatedClass(path, className);
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

    public List<EmbeddedResource> getMaterializedResources() {
        return resources.values().stream()
            .sorted(Comparator.comparing(EmbeddedResource::logicalPath))
            .toList();
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

    private RuntimeException collision(Artifact first, Artifact second) {
        List<Artifact> ordered = Stream.of(first, second).sorted(ARTIFACT_ORDER).toList();
        Artifact firstArtifact = ordered.get(0);
        Artifact secondArtifact = ordered.get(1);

        EmbeddedResource resource = firstArtifact.resource() != null
            ? firstArtifact.resource()
            : secondArtifact.resource();

        SourceInfo sourceInfo = resource != null
            ? resource.declarationSourceInfo()
            : SourceInfo.none();

        return ResourceErrors.resourceFileCollision(
            sourceInfo,
            firstArtifact.path(),
            firstArtifact.description(),
            secondArtifact.description());
    }

    private record Artifact(String path, String description, EmbeddedResource resource) {

        private static Artifact resource(String path, EmbeddedResource resource) {
            SourceInfo declaration = resource.declarationSourceInfo().toOriginal();
            String description = "%s resource '%s' declared at %s".formatted(
                resource.declaringSource(), resource.logicalName(), declaration.getStart());

            return new Artifact(path, description, resource);
        }

        private static Artifact generatedClass(String path, String className) {
            return new Artifact(path, "generated class '" + className + "'", null);
        }

        private boolean sameDeclaration(Artifact other) {
            return resource != null
                && resource == other.resource
                && path.equals(other.path);
        }
    }
}
