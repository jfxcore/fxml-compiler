// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.text;

import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.TypeNode;
import org.jfxcore.compiler.ast.ValueNode;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.jfxcore.compiler.type.KnownSymbols.*;

/**
 * Syntax for a leading or enclosing-instance-qualified constructor invocation.
 */
public class ConstructorNode extends DerivedTextNode {

    private final List<PathNode> constructorWitnesses;
    private final List<PathNode> classArguments;
    private final List<ValueNode> arguments;
    private final @Nullable SourceInfo qualifierSeparatorSourceInfo;
    private final SourceInfo newSourceInfo;
    private final @Nullable SourceInfo constructorWitnessSourceInfo;
    private final @Nullable SourceInfo classArgumentsSourceInfo;
    private final SourceInfo openParenSourceInfo;
    private final SourceInfo closeParenSourceInfo;

    private @Nullable ValueNode qualifier;
    private PathNode constructedType;

    public ConstructorNode(
            @Nullable ValueNode qualifier,
            Collection<? extends PathNode> constructorWitnesses,
            PathNode constructedType,
            Collection<? extends PathNode> classArguments,
            Collection<? extends ValueNode> arguments,
            @Nullable SourceInfo qualifierSeparatorSourceInfo,
            SourceInfo newSourceInfo,
            @Nullable SourceInfo constructorWitnessSourceInfo,
            @Nullable SourceInfo classArgumentsSourceInfo,
            SourceInfo openParenSourceInfo,
            SourceInfo closeParenSourceInfo,
            SourceInfo sourceInfo) {
        this(qualifier, constructorWitnesses, constructedType, classArguments, arguments,
             qualifierSeparatorSourceInfo, newSourceInfo, constructorWitnessSourceInfo,
             classArgumentsSourceInfo, openParenSourceInfo, closeParenSourceInfo,
             null, sourceInfo);
    }

    private ConstructorNode(
            @Nullable ValueNode qualifier,
            Collection<? extends PathNode> constructorWitnesses,
            PathNode constructedType,
            Collection<? extends PathNode> classArguments,
            Collection<? extends ValueNode> arguments,
            @Nullable SourceInfo qualifierSeparatorSourceInfo,
            SourceInfo newSourceInfo,
            @Nullable SourceInfo constructorWitnessSourceInfo,
            @Nullable SourceInfo classArgumentsSourceInfo,
            SourceInfo openParenSourceInfo,
            SourceInfo closeParenSourceInfo,
            @Nullable TypeNode type,
            SourceInfo sourceInfo) {
        super(type != null ? type : new TypeNode(StringName, sourceInfo), sourceInfo);
        this.qualifier = qualifier;
        this.constructorWitnesses = new ArrayList<>(checkNotNull(constructorWitnesses));
        this.constructedType = checkNotNull(constructedType);
        this.classArguments = new ArrayList<>(checkNotNull(classArguments));
        this.arguments = new ArrayList<>(checkNotNull(arguments));
        this.qualifierSeparatorSourceInfo = qualifierSeparatorSourceInfo;
        this.newSourceInfo = checkNotNull(newSourceInfo);
        this.constructorWitnessSourceInfo = constructorWitnessSourceInfo;
        this.classArgumentsSourceInfo = classArgumentsSourceInfo;
        this.openParenSourceInfo = checkNotNull(openParenSourceInfo);
        this.closeParenSourceInfo = checkNotNull(closeParenSourceInfo);
    }

    public @Nullable ValueNode getQualifier() {
        return qualifier;
    }

    public List<PathNode> getConstructorWitnesses() {
        return constructorWitnesses;
    }

    public PathNode getConstructedType() {
        return constructedType;
    }

    public List<PathNode> getClassArguments() {
        return classArguments;
    }

    public List<ValueNode> getArguments() {
        return arguments;
    }

    public @Nullable SourceInfo getQualifierSeparatorSourceInfo() {
        return qualifierSeparatorSourceInfo;
    }

    public SourceInfo getNewSourceInfo() {
        return newSourceInfo;
    }

    public @Nullable SourceInfo getConstructorWitnessSourceInfo() {
        return constructorWitnessSourceInfo;
    }

    public @Nullable SourceInfo getClassArgumentsSourceInfo() {
        return classArgumentsSourceInfo;
    }

    public SourceInfo getOpenParenSourceInfo() {
        return openParenSourceInfo;
    }

    public SourceInfo getCloseParenSourceInfo() {
        return closeParenSourceInfo;
    }

    @Override
    public String formatText() {
        var builder = new StringBuilder();

        if (qualifier != null) {
            builder.append(formatValue(qualifier)).append('.');
        }

        builder.append("new ");

        if (!constructorWitnesses.isEmpty()) {
            builder.append('<').append(formatPaths(constructorWitnesses)).append("> ");
        }

        builder.append(constructedType.formatText());

        if (!classArguments.isEmpty()) {
            builder.append('<').append(formatPaths(classArguments)).append('>');
        }

        builder.append('(')
            .append(arguments.stream().map(TextNode::formatValue).collect(Collectors.joining(",")))
            .append(')');

        return builder.toString();
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        super.acceptChildren(visitor);

        if (qualifier != null) {
            qualifier = (ValueNode)qualifier.accept(visitor);
        }

        acceptChildren(constructorWitnesses, visitor, PathNode.class);
        constructedType = (PathNode)constructedType.accept(visitor);
        acceptChildren(classArguments, visitor, PathNode.class);
        acceptChildren(arguments, visitor, ValueNode.class);
    }

    @Override
    public ConstructorNode deepClone() {
        return new ConstructorNode(
            qualifier != null ? qualifier.deepClone() : null,
            deepClone(constructorWitnesses),
            constructedType.deepClone(),
            deepClone(classArguments),
            deepClone(arguments),
            qualifierSeparatorSourceInfo,
            newSourceInfo,
            constructorWitnessSourceInfo,
            classArgumentsSourceInfo,
            openParenSourceInfo,
            closeParenSourceInfo,
            getType().deepClone(),
            getSourceInfo()).copy(this);
    }

    @Override
    public boolean equals(Object o) {
        if (!super.equals(o)) return false;
        ConstructorNode that = (ConstructorNode)o;
        return Objects.equals(qualifier, that.qualifier)
            && constructorWitnesses.equals(that.constructorWitnesses)
            && constructedType.equals(that.constructedType)
            && classArguments.equals(that.classArguments)
            && arguments.equals(that.arguments);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            super.hashCode(), qualifier, constructorWitnesses, constructedType, classArguments, arguments);
    }

    private static String formatPaths(Collection<? extends PathNode> paths) {
        return paths.stream().map(PathNode::formatText).collect(Collectors.joining(","));
    }
}
