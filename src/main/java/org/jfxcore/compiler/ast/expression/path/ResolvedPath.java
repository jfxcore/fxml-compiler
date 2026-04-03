// Copyright (c) 2022, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.expression.path;

import org.jfxcore.compiler.ast.emit.ValueEmitterNode;
import org.jfxcore.compiler.ast.text.PathNode;
import org.jfxcore.compiler.ast.text.PathSegmentNode;
import org.jfxcore.compiler.ast.text.SubPathSegmentNode;
import org.jfxcore.compiler.ast.text.TextSegmentNode;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.diagnostic.errors.ParserErrors;
import org.jfxcore.compiler.diagnostic.errors.SymbolResolutionErrors;
import org.jfxcore.compiler.type.FieldDeclaration;
import org.jfxcore.compiler.type.MethodDeclaration;
import org.jfxcore.compiler.type.Resolver;
import org.jfxcore.compiler.type.TypeDeclaration;
import org.jfxcore.compiler.type.TypeInstance;
import org.jfxcore.compiler.type.TypeInvoker;
import org.jfxcore.compiler.util.ObservableKind;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.jfxcore.compiler.type.KnownSymbols.*;

/**
 * Represents a property path like 'foo.bar.baz', where each of the segments
 * correspond to a field, getter or property getter on a class.
 */
public class ResolvedPath {

    private final SourceInfo sourceInfo;
    private final List<Segment> segments;

    public static ResolvedPath parse(Segment firstSegment,
                                     List<PathSegmentNode> path,
                                     boolean preferObservable,
                                     SourceInfo sourceInfo) {
        return new ResolvedPath(firstSegment, path, false, preferObservable, sourceInfo);
    }

    public static ResolvedPath parse(Segment firstSegment,
                                     List<PathSegmentNode> path,
                                     boolean staticContext,
                                     boolean preferObservable,
                                     SourceInfo sourceInfo) {
        return new ResolvedPath(firstSegment, path, staticContext, preferObservable, sourceInfo);
    }

    private ResolvedPath(List<Segment> segments, SourceInfo sourceInfo) {
        this.sourceInfo = sourceInfo;
        this.segments = segments;
    }

    private ResolvedPath(Segment firstSegment,
                         List<PathSegmentNode> path,
                         boolean staticContext,
                         boolean preferObservable,
                         SourceInfo sourceInfo) {
        this.sourceInfo = sourceInfo;
        this.segments = new ArrayList<>(path.size() + 1);
        this.segments.add(firstSegment);

        if (!path.isEmpty() && path.get(0).equals("this")) {
            if (path.size() == 1) {
                return;
            }

            path = path.stream().skip(1).toList();
        }

        for (PathSegmentNode part : path) {
            if (part.equals("this")) {
                throw ParserErrors.invalidExpression(sourceInfo);
            }
        }

        if (path.isEmpty()) {
            throw ParserErrors.invalidExpression(sourceInfo);
        }

        Resolver resolver = new Resolver(sourceInfo);
        TypeInvoker invoker = new TypeInvoker(sourceInfo);
        TypeDeclaration currentHostType = segments.get(0).getValueTypeInstance().declaration();

        for (PathSegmentNode segment : path) {
            Segment source = getValueSource(
                resolver, invoker, segment, currentHostType, staticContext, preferObservable, false);

            if (source == null) {
                if (segment.isObservableSelector() && getValueSource(
                        resolver, invoker, segment, currentHostType, staticContext, preferObservable, true) != null) {
                    throw SymbolResolutionErrors.invalidInvariantReference(
                        segment.getSourceInfo(), currentHostType, segment.getText());
                } else {
                    if (segment instanceof SubPathSegmentNode subPathSegment) {
                        var segments = subPathSegment.getSegments();
                        var declaringClassName = segments.stream()
                            .limit(segments.size() - 1)
                            .map(PathSegmentNode::getText)
                            .collect(Collectors.joining("."));

                        SourceInfo declaringClassSourceInfo = SourceInfo.span(
                            segments.get(0).getSourceInfo(), segments.get(segments.size() - 2).getSourceInfo());

                        var declaringClass = new Resolver(declaringClassSourceInfo)
                            .resolveClassAgainstImports(declaringClassName);

                        throw SymbolResolutionErrors.memberNotFound(
                            segments.get(segments.size() - 1).getSourceInfo(),
                            declaringClass, segments.get(segments.size() - 1).getText());
                    }

                    throw SymbolResolutionErrors.memberNotFound(
                        segment.getSourceInfo(), currentHostType, segment.getText());
                }
            }

            segments.add(source);
            currentHostType = source.getValueTypeInstance().declaration();
            staticContext = false;
        }

        optimizePath();
    }

    public ResolvedPath subPath(int from, int to) {
        return new ResolvedPath(new ArrayList<>(segments.subList(from, to)), sourceInfo);
    }

    public Segment get(int index) {
        return segments.get(index);
    }

    public int size() {
        return segments.size();
    }

    public TypeInstance getTypeInstance() {
        return segments.get(segments.size() - 1).getTypeInstance();
    }

    public TypeInstance getValueTypeInstance() {
        return segments.get(segments.size() - 1).getValueTypeInstance();
    }

    public ObservableKind getObservableKind() {
        return segments.get(segments.size() - 1).getObservableKind();
    }

    public boolean isInvariant() {
        for (int i = 0; i < size(); ++i) {
            if (segments.get(i).getObservableKind() != ObservableKind.NONE) {
                return false;
            }
        }

        return true;
    }

    /**
     * Transforms a path into a sequence of code-emitting nodes that resolve the value of the path at runtime,
     * i.e. get the value of each of the path segments until the final value has been resolved.
     */
    public List<ValueEmitterNode> toValueEmitters(boolean requireNonNull, SourceInfo sourceInfo) {
        List<ValueEmitterNode> list = new ArrayList<>();

        for (int i = 0; i < segments.size(); ++i) {
            list.add(segments.get(i).toValueEmitter(i < segments.size() - 1 || requireNonNull, sourceInfo));
        }

        return list;
    }

    /**
     * Returns whether the specified path contains any observable segment.
     */
    public boolean isObservable() {
        for (Segment segment : segments) {
            if (segment.getObservableKind() != ObservableKind.NONE) {
                return true;
            }
        }

        return false;
    }

    /**
     * Returns the {@link FoldedPath} representation of the current path.
     */
    public FoldedPath fold() {
        List<FoldedGroup> result = new ArrayList<>();
        int i = 0;

        while (segments.get(i).getObservableKind() == ObservableKind.NONE) {
            ++i;
        }

        while (i < segments.size()) {
            int start = i++;

            while (i < segments.size() && segments.get(i).getObservableKind() == ObservableKind.NONE) {
                ++i;
            }

            result.add(
                new FoldedGroup(
                    segments.subList(start, Math.min(i + 1, segments.size())).toArray(new Segment[0]),
                    getSegmentName(segments, start)));
        }

        return new FoldedPath(sourceInfo, result);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ResolvedPath path = (ResolvedPath)o;
        return segments.equals(path.segments);
    }

    @Override
    public int hashCode() {
        return segments.hashCode();
    }

    /**
     * If the path includes static segments (i.e. static fields or static getters), we can
     * remove everything before the last static segment, as we don't need those segments
     * to resolve the path at runtime.
     * Note that we don't remove attached property getters, only regular static getters.
     */
    private void optimizePath() {
        for (int i = segments.size() - 1; i >= 0; --i) {
            boolean isStatic =
                segments.get(i) instanceof FieldSegment fieldSegment && fieldSegment.getField().isStatic()
                || segments.get(i) instanceof GetterSegment getterSegment
                    && !getterSegment.isStaticPropertyGetter()
                    && getterSegment.getGetter().isStatic();

            if (isStatic) {
                segments.subList(0, i).clear();
                break;
            }
        }
    }

    private Segment getValueSource(
            Resolver resolver,
            TypeInvoker invoker,
            PathSegmentNode segment,
            TypeDeclaration declaringClass,
            boolean staticContext,
            boolean preferObservable,
            boolean suppressObservableSelector) {
        ResolveSegmentMethod[] methods = new ResolveSegmentMethod[] {
            this::getPathSegmentFromField,
            this::getPathSegmentFromGetter
        };

        boolean attachedProperty = false;
        TypeDeclaration receiverClass = declaringClass;
        String propertyName = segment.getText();

        if (segment instanceof SubPathSegmentNode subPath) {
            for (PathSegmentNode subSegment : subPath.getSegments()) {
                if (subSegment instanceof SubPathSegmentNode || subSegment.isObservableSelector()) {
                    throw ParserErrors.invalidExpression(segment.getSourceInfo());
                }
            }

            attachedProperty = true;
            List<PathSegmentNode> segments = subPath.getSegments();
            propertyName = segments.get(segments.size() - 1).getText();

            for (int i = 0; i < segments.size() - 2; ++i) {
                if (segments.get(i) instanceof TextSegmentNode t && !t.getWitnesses().isEmpty()) {
                    throw ParserErrors.unexpectedExpression(SourceInfo.span(t.getWitnesses()));
                }
            }

            String declaringClassName = segments.stream()
                .limit(segments.size() - 1)
                .map(PathSegmentNode::getText)
                .collect(Collectors.joining("."));

            SourceInfo declaringClassSourceInfo = SourceInfo.span(
                segments.get(0).getSourceInfo(), segments.get(segments.size() - 2).getSourceInfo());

            declaringClass = new Resolver(declaringClassSourceInfo).resolveClassAgainstImports(declaringClassName);
            resolver = new Resolver(segments.get(segments.size() - 1).getSourceInfo());
        }

        List<TypeInstance> providedArguments = segment.getWitnesses().stream().map(PathNode::resolve).toList();
        boolean selectObservable = segment.isObservableSelector() && !suppressObservableSelector;
        String propertyNameUpper = Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
        SegmentMap segments = new SegmentMap();

        for (ResolveSegmentMethod method : methods) {
            segments.tryAdd(
                method.resolve(
                    resolver, invoker, propertyName, declaringClass, receiverClass, staticContext,
                    attachedProperty, selectObservable, providedArguments),
                0, preferObservable, selectObservable);

            segments.tryAdd(
                method.resolve(
                    resolver, invoker, String.format("%sProperty", propertyName), declaringClass, receiverClass,
                    staticContext, attachedProperty, selectObservable, providedArguments),
                1, preferObservable, selectObservable);

            segments.tryAdd(
                method.resolve(
                    resolver, invoker, String.format("get%s", propertyNameUpper), declaringClass, receiverClass,
                    staticContext, attachedProperty, selectObservable, providedArguments),
                2, false, selectObservable);

            segments.tryAdd(
                method.resolve(
                    resolver, invoker, String.format("is%s", propertyNameUpper), declaringClass, receiverClass,
                    staticContext, attachedProperty, selectObservable, providedArguments),
                3, false, selectObservable);
        }

        Iterator<SegmentInfo> it = segments.values().iterator();
        if (it.hasNext()) {
            return it.next().segment;
        }

        return null;
    }

    /**
     * Computes the score of the resolved path segment, which is used to rank potential path interpretations.
     */
    private static int getSegmentScore(SegmentInfo info, int nameOrder, boolean preferObservable) {
        int observable;

        if (preferObservable) {
            observable = info.segment.getObservableKind() != ObservableKind.NONE ? -100 : 0;
        } else {
            observable = info.segment.getObservableKind() != ObservableKind.NONE ? 0 : -100;
        }

        int source = 0;

        if (info.segment instanceof FieldSegment) {
            source = -20;
        }

        return observable + source + nameOrder;
    }

    /**
     * Tries to resolve a path segment against a public field.
     */
    private SegmentInfo getPathSegmentFromField(
            Resolver resolver,
            TypeInvoker invoker,
            String propertyName,
            TypeDeclaration declaringClass,
            TypeDeclaration receiverClass,
            boolean staticContext,
            boolean attachedProperty,
            boolean selectObservable,
            List<TypeInstance> providedArguments) {
        if (attachedProperty) {
            return null;
        }

        FieldDeclaration fieldDeclaration = resolver.tryResolveField(declaringClass, propertyName);
        if (fieldDeclaration == null) {
            return null;
        }

        TypeDeclaration fieldType = fieldDeclaration.type();
        ObservableKind observableKind = ObservableKind.get(fieldType);

        if (selectObservable && observableKind == ObservableKind.NONE) {
            return null;
        }

        List<TypeInstance> invocationContext = segments.stream()
            .map(Segment::getTypeInstance)
            .collect(Collectors.toList());

        TypeInstance type = invoker.invokeFieldType(fieldDeclaration, invocationContext);

        if (selectObservable) {
            return new SegmentInfo(
                new FieldSegment(fieldDeclaration.name(), propertyName, type, type, fieldDeclaration, ObservableKind.NONE),
                true);
        }

        TypeInstance valueType = observableKind != ObservableKind.NONE ?
            resolver.findObservableArgument(type) : type;

        FieldSegment segment = new FieldSegment(
            fieldDeclaration.name(), propertyName, type, valueType, fieldDeclaration, observableKind);

        return new SegmentInfo(segment, observableKind.isReadOnly());
    }

    /**
     * Tries to resolve a path segment against a getter method.
     */
    private SegmentInfo getPathSegmentFromGetter(
            Resolver resolver,
            TypeInvoker invoker,
            String propertyName,
            TypeDeclaration declaringClass,
            TypeDeclaration receiverClass,
            boolean staticContext,
            boolean attachedProperty,
            boolean selectObservable,
            List<TypeInstance> providedArguments) {
        MethodDeclaration getterDeclaration = attachedProperty ?
            resolver.tryResolveStaticGetter(declaringClass, receiverClass, propertyName, true) :
            resolver.tryResolveGetter(declaringClass, propertyName, true, null);

        if (getterDeclaration == null) {
            return null;
        }

        if (!attachedProperty && staticContext && !getterDeclaration.isStatic()) {
            throw SymbolResolutionErrors.instanceMemberReferencedFromStaticContext(sourceInfo, getterDeclaration);
        }

        List<TypeInstance> invocationContext = segments.stream().map(segment -> {
            if (segment.getObservableKind() == ObservableKind.NONE) {
                return segment.getTypeInstance();
            }

            return segment.getValueTypeInstance();
        }).collect(Collectors.toList());

        TypeDeclaration returnType = getterDeclaration.returnType();
        ObservableKind observableKind = ObservableKind.get(returnType);

        if (selectObservable && observableKind == ObservableKind.NONE) {
            return null;
        }

        TypeInstance type = invoker.invokeReturnType(getterDeclaration, invocationContext, providedArguments);

        if (selectObservable) {
            return new SegmentInfo(
                new GetterSegment(
                    getterDeclaration.name(), propertyName, type, type,
                    getterDeclaration, attachedProperty, ObservableKind.NONE),
                true);
        }

        TypeInstance valueType = observableKind != ObservableKind.NONE ?
            resolver.findObservableArgument(type) : type;

        return new SegmentInfo(
            new GetterSegment(
                getterDeclaration.name(), propertyName, type, valueType,
                getterDeclaration, attachedProperty, observableKind),
            observableKind.isReadOnly());
    }

    /**
     * Returns a unique name for the specified path segment that can be used as a class name.
     */
    private static String getSegmentName(List<Segment> path, int segment) {
        StringBuilder stringBuilder = new StringBuilder();

        for (Segment pathSegment : path) {
            if (pathSegment instanceof FieldSegment || pathSegment instanceof GetterSegment) {
                stringBuilder.append(pathSegment.getName()).append("$");
            }
        }

        stringBuilder.append(segment);

        return stringBuilder.toString();
    }

    private interface ResolveSegmentMethod {
        SegmentInfo resolve(
            Resolver resolver,
            TypeInvoker invoker,
            String propertyName,
            TypeDeclaration declaringClass,
            TypeDeclaration receiverClass,
            boolean staticContext,
            boolean attachedProperty,
            boolean selectObservable,
            List<TypeInstance> providedArguments);
    }

    private static class SegmentMap extends TreeMap<Integer, SegmentInfo> {
        public SegmentMap() {
            super(Comparator.naturalOrder());
        }

        public void tryAdd(SegmentInfo segmentInfo, int nameOrder, boolean preferObservable,
                           boolean acceptOnlyObservable) {
            if (segmentInfo == null || containsValue(segmentInfo)) {
                return;
            }

            if (!acceptOnlyObservable ||
                    segmentInfo.segment().getTypeInstance().subtypeOf(ObservableValueDecl())) {
                put(getSegmentScore(segmentInfo, nameOrder, preferObservable), segmentInfo);
            }
        }
    }

    private record SegmentInfo(Segment segment, boolean readonly) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SegmentInfo that = (SegmentInfo) o;
            return Objects.equals(segment, that.segment);
        }

        @Override
        public int hashCode() {
            return segment.hashCode();
        }
    }

    private record KotlinDelegateInfo(FieldDeclaration delegateField, MethodDeclaration getter, boolean publicSetter) {}
}
