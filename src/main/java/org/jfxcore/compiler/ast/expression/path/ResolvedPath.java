// Copyright (c) 2022, 2023, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.expression.path;

import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.Modifier;
import javassist.NotFoundException;
import javassist.bytecode.annotation.Annotation;
import kotlinx.metadata.Flag;
import kotlinx.metadata.KmClass;
import kotlinx.metadata.KmExtensionType;
import kotlinx.metadata.KmProperty;
import kotlinx.metadata.KmPropertyExtensionVisitor;
import kotlinx.metadata.KmPropertyVisitor;
import kotlinx.metadata.jvm.JvmFieldSignature;
import kotlinx.metadata.jvm.JvmMethodSignature;
import kotlinx.metadata.jvm.JvmPropertyExtensionVisitor;
import kotlinx.metadata.jvm.KotlinClassHeader;
import kotlinx.metadata.jvm.KotlinClassMetadata;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.emit.ValueEmitterNode;
import org.jfxcore.compiler.ast.text.PathSegmentNode;
import org.jfxcore.compiler.ast.text.SubPathSegmentNode;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.diagnostic.errors.ParserErrors;
import org.jfxcore.compiler.diagnostic.errors.SymbolResolutionErrors;
import org.jfxcore.compiler.util.Classes;
import org.jfxcore.compiler.util.ObservableKind;
import org.jfxcore.compiler.util.Resolver;
import org.jfxcore.compiler.util.TypeHelper;
import org.jfxcore.compiler.util.TypeInstance;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.jfxcore.compiler.util.ExceptionHelper.*;

/**
 * Represents a property path like 'foo.bar.baz', where each of the segments
 * correspond to a field, getter or property getter on a class.
 */
public class ResolvedPath {

    private final SourceInfo sourceInfo;
    private final List<Segment> segments;

    public static ResolvedPath parse(
            Segment firstSegment, List<PathSegmentNode> path, boolean preferObservable, SourceInfo sourceInfo) {
        return new ResolvedPath(firstSegment, path, preferObservable, sourceInfo);
    }

    private ResolvedPath(List<Segment> segments, SourceInfo sourceInfo) {
        this.sourceInfo = sourceInfo;
        this.segments = segments;
    }

    private ResolvedPath(Segment firstSegment, List<PathSegmentNode> path, boolean preferObservable, SourceInfo sourceInfo) {
        this.sourceInfo = sourceInfo;
        this.segments = new ArrayList<>(path.size() + 1);
        this.segments.add(firstSegment);

        if (path.size() > 0 && path.get(0).equals("this")) {
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

        if (path.size() == 0) {
            throw ParserErrors.invalidExpression(sourceInfo);
        }

        Resolver resolver = new Resolver(sourceInfo);
        CtClass currentHostType = segments.get(0).getValueTypeInstance().jvmType();

        for (PathSegmentNode segment : path) {
            Segment source = getValueSource(resolver, segment, currentHostType, preferObservable, false);

            if (source == null) {
                if (segment.isObservableSelector() && getValueSource(
                        resolver, segment, currentHostType, preferObservable, true) != null) {
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
            currentHostType = source.getValueTypeInstance().jvmType();
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
    public List<ValueEmitterNode> toValueEmitters(SourceInfo sourceInfo) {
        List<ValueEmitterNode> list = new ArrayList<>();

        for (Segment segment : segments) {
            list.add(segment.toValueEmitter(sourceInfo));
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
                segments.get(i) instanceof FieldSegment fieldSegment
                    && Modifier.isStatic(fieldSegment.getField().getModifiers())
                || segments.get(i) instanceof GetterSegment getterSegment
                    && !getterSegment.isStaticPropertyGetter()
                    && Modifier.isStatic(getterSegment.getGetter().getModifiers());

            if (isStatic) {
                segments.subList(0, i).clear();
                break;
            }
        }
    }

    private Segment getValueSource(
            Resolver resolver,
            PathSegmentNode segment,
            CtClass declaringClass,
            boolean preferObservable,
            boolean suppressObservableSelector) {
        ResolveSegmentMethod[] methods = new ResolveSegmentMethod[] {
                this::getPathSegmentFromField,
                this::getPathSegmentFromGetter,
                this::getPathSegmentFromKotlinDelegate
        };

        boolean attachedProperty = false;
        CtClass receiverClass = declaringClass;
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

            String declaringClassName = segments.stream()
                .limit(segments.size() - 1)
                .map(PathSegmentNode::getText)
                .collect(Collectors.joining("."));

            SourceInfo declaringClassSourceInfo = SourceInfo.span(
                segments.get(0).getSourceInfo(), segments.get(segments.size() - 2).getSourceInfo());

            declaringClass = new Resolver(declaringClassSourceInfo).resolveClassAgainstImports(declaringClassName);
            resolver = new Resolver(segments.get(segments.size() - 1).getSourceInfo());
        }

        boolean selectObservable = segment.isObservableSelector() && !suppressObservableSelector;
        String propertyNameUpper = Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
        SegmentMap segments = new SegmentMap();

        for (ResolveSegmentMethod method : methods) {
            segments.tryAdd(
                method.resolve(
                    resolver, propertyName, declaringClass, receiverClass, attachedProperty, selectObservable),
                0, preferObservable, selectObservable);

            segments.tryAdd(
                method.resolve(
                    resolver, String.format("%sProperty", propertyName), declaringClass, receiverClass,
                    attachedProperty, selectObservable),
                1, preferObservable, selectObservable);

            segments.tryAdd(
                method.resolve(
                    resolver, String.format("get%s", propertyNameUpper), declaringClass, receiverClass,
                    attachedProperty, selectObservable),
                2, false, selectObservable);

            segments.tryAdd(
                method.resolve(
                    resolver, String.format("is%s", propertyNameUpper), declaringClass, receiverClass,
                    attachedProperty, selectObservable),
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
            String propertyName,
            CtClass declaringClass,
            CtClass receiverClass,
            boolean attachedProperty,
            boolean selectObservable) {
        if (attachedProperty) {
            return null;
        }

        CtField field = resolver.tryResolveField(declaringClass, propertyName);
        if (field == null) {
            return null;
        }

        CtClass fieldType;
        try {
            fieldType = field.getType();
        } catch (NotFoundException ex) {
            throw SymbolResolutionErrors.classNotFound(sourceInfo, ex.getMessage());
        }

        ObservableKind observableKind = ObservableKind.get(fieldType);
        if (selectObservable && observableKind == ObservableKind.NONE) {
            return null;
        }

        List<TypeInstance> invocationChain = segments.stream().map(Segment::getTypeInstance).collect(Collectors.toList());
        TypeInstance type = resolver.getTypeInstance(field, invocationChain);

        if (selectObservable) {
            return new SegmentInfo(
                new FieldSegment(field.getName(), propertyName, type, type, field, ObservableKind.NONE),
                true);
        }

        TypeInstance valueType = observableKind != ObservableKind.NONE ?
            resolver.findObservableArgument(type) : type;

        return new SegmentInfo(
            new FieldSegment(field.getName(), propertyName, type, valueType, field, observableKind),
            observableKind.isReadOnly());
    }

    /**
     * Tries to resolve a path segment against a getter method.
     */
    private SegmentInfo getPathSegmentFromGetter(
            Resolver resolver,
            String propertyName,
            CtClass declaringClass,
            CtClass receiverClass,
            boolean attachedProperty,
            boolean selectObservable) {
        CtMethod getter = attachedProperty ?
            resolver.tryResolveStaticGetter(declaringClass, receiverClass, propertyName, true) :
            resolver.tryResolveGetter(declaringClass, propertyName, true, null);

        if (getter == null) {
            return null;
        }

        List<TypeInstance> invocationChain = segments.stream().map(segment -> {
            if (segment.getObservableKind() == ObservableKind.NONE) {
                return segment.getTypeInstance();
            }

            return segment.getValueTypeInstance();
        }).collect(Collectors.toList());

        CtClass returnType;
        try {
            returnType = getter.getReturnType();
        } catch (NotFoundException ex) {
            throw SymbolResolutionErrors.classNotFound(sourceInfo, ex.getMessage());
        }

        ObservableKind observableKind = ObservableKind.get(returnType);
        if (selectObservable && observableKind == ObservableKind.NONE) {
            return null;
        }

        TypeInstance type = resolver.getTypeInstance(getter, invocationChain);

        if (selectObservable) {
            return new SegmentInfo(
                new GetterSegment(getter.getName(), propertyName, type, type, getter, attachedProperty, ObservableKind.NONE),
                true);
        }

        TypeInstance valueType = observableKind != ObservableKind.NONE ?
            resolver.findObservableArgument(type) : type;

        return new SegmentInfo(
            new GetterSegment(getter.getName(), propertyName, type, valueType, getter, attachedProperty, observableKind),
            observableKind.isReadOnly());
    }

    private SegmentInfo getPathSegmentFromKotlinDelegate(
            Resolver resolver,
            String propertyName,
            CtClass declaringClass,
            CtClass receiverClass,
            boolean attachedProperty,
            boolean selectObservable) {
        if (attachedProperty) {
            return null;
        }

        KotlinDelegateInfo delegateInfo = getKotlinDelegateInfo(resolver, declaringClass, propertyName);
        if (delegateInfo == null) {
            return null;
        }

        List<TypeInstance> invocationChain = segments.stream().map(segment -> {
            if (segment.getObservableKind() == ObservableKind.NONE) {
                return segment.getTypeInstance();
            }

            return segment.getValueTypeInstance();
        }).collect(Collectors.toList());

        CtClass fieldType;
        try {
            fieldType = delegateInfo.delegateField.getType();
        } catch (NotFoundException ex) {
            throw SymbolResolutionErrors.classNotFound(sourceInfo, ex.getMessage());
        }

        ObservableKind observableKind = ObservableKind.get(fieldType);
        if (selectObservable && observableKind == ObservableKind.NONE) {
            return null;
        }

        if (!delegateInfo.publicSetter) {
            observableKind = observableKind.toReadOnly();
        }

        TypeInstance valueType = resolver.getTypeInstance(delegateInfo.getter, invocationChain);
        TypeInstance type = resolver.getTypeInstance(fieldType);
        TypeInstance argument = resolver.tryFindObservableArgument(type);

        CtClass returnType;
        try {
            returnType = delegateInfo.getter.getReturnType();
        } catch (NotFoundException ex) {
            throw SymbolResolutionErrors.classNotFound(sourceInfo, ex.getMessage());
        }

        if (argument == null || !returnType.equals(argument.jvmType())) {
            type = resolver.getTypeInstance(type.jvmType(), List.of(valueType));
        }

        if (selectObservable) {
            return new SegmentInfo(
                new KotlinDelegateSegment(
                    delegateInfo.delegateField.getName(), propertyName, type, type,
                    delegateInfo.delegateField, ObservableKind.NONE),
                observableKind.isReadOnly());
        }

        TypeInstance typeArg = observableKind != ObservableKind.NONE ? valueType : type;

        return new SegmentInfo(
            new KotlinDelegateSegment(
                delegateInfo.delegateField.getName(), propertyName, type, typeArg,
                delegateInfo.delegateField, observableKind),
            observableKind.isReadOnly());
    }

    @Nullable
    private KotlinDelegateInfo getKotlinDelegateInfo(Resolver resolver, CtClass declaringType, String name) {
        Annotation kotlinMetadataAnnotation = resolver.tryResolveClassAnnotation(declaringType, "kotlin.Metadata");
        if (kotlinMetadataAnnotation == null) {
            return null;
        }

        KotlinClassMetadata metadata = KotlinClassMetadata.read(new KotlinClassHeader(
            TypeHelper.getAnnotationInt(kotlinMetadataAnnotation, "k"),
            TypeHelper.getAnnotationIntArray(kotlinMetadataAnnotation, "mv"),
            TypeHelper.getAnnotationStringArray(kotlinMetadataAnnotation, "d1"),
            TypeHelper.getAnnotationStringArray(kotlinMetadataAnnotation, "d2"),
            null,
            declaringType.getPackageName(),
            null));

        if (!(metadata instanceof KotlinClassMetadata.Class classMetadata)) {
            return null;
        }

        KmClass kmClass = classMetadata.toKmClass();

        for (KmProperty property : kmClass.getProperties()) {
            if (!property.getName().equals(name)
                    || !Flag.Property.IS_DELEGATED.invoke(property.getFlags())
                    || !Flag.IS_PUBLIC.invoke(property.getFlags())) {
                continue;
            }

            KotlinDelegateInfo[] result = new KotlinDelegateInfo[1];

            property.accept(new KmPropertyVisitor() {
                @Nullable
                @Override
                public KmPropertyExtensionVisitor visitExtensions(@NotNull KmExtensionType type) {
                    if (type != JvmPropertyExtensionVisitor.TYPE) {
                        return null;
                    }

                    return new JvmPropertyExtensionVisitor() {
                        @Override
                        public void visit(
                                int jvmFlags,
                                @Nullable JvmFieldSignature fieldSignature,
                                @Nullable JvmMethodSignature getterSignature,
                                @Nullable JvmMethodSignature setterSignature) {
                            boolean publicSetter = false;

                            if (fieldSignature == null) {
                                throw new NullPointerException("fieldSignature");
                            }

                            if (getterSignature == null) {
                                throw new NullPointerException("getterSignature");
                            }

                            if (setterSignature != null) {
                                CtMethod setter = resolver.tryResolveMethod(
                                    declaringType, m -> m.getName().equals(setterSignature.getName()));

                                publicSetter = setter != null && Modifier.isPublic(setter.getModifiers());
                            }

                            CtField field = resolver.resolveField(declaringType, fieldSignature.getName(), false);
                            CtMethod getter = resolver.resolveGetter(declaringType, getterSignature.getName(), true, null);

                            if (unchecked(sourceInfo, () -> field.getType().subtypeOf(Classes.ObservableValueType()))) {
                                result[0] = new KotlinDelegateInfo(field, getter, publicSetter);
                            } else {
                                result[0] = null;
                            }
                        }
                    };
                }
            });

            return result[0];
        }

        return null;
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
            String propertyName,
            CtClass declaringClass,
            CtClass receiverClass,
            boolean attachedProperty,
            boolean selectObservable);
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
                    segmentInfo.segment().getTypeInstance().subtypeOf(Classes.ObservableValueType())) {
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

    private record KotlinDelegateInfo(CtField delegateField, CtMethod getter, boolean publicSetter) {}

}
