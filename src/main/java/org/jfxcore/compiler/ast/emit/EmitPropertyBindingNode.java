// Copyright (c) 2021, 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.ast.emit;

import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.AbstractNode;
import org.jfxcore.compiler.ast.BindingMode;
import org.jfxcore.compiler.ast.GeneratorEmitterNode;
import org.jfxcore.compiler.ast.NodeDataKey;
import org.jfxcore.compiler.ast.Visitor;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.generate.Generator;
import org.jfxcore.compiler.generate.InvertBooleanBindingGenerator;
import org.jfxcore.compiler.generate.ClassGenerator;
import org.jfxcore.compiler.generate.PushListenerGenerator;
import org.jfxcore.compiler.generate.ReferenceTrackerGenerator;
import org.jfxcore.compiler.type.BehaviorDeclaration;
import org.jfxcore.compiler.type.Resolver;
import org.jfxcore.compiler.type.TypeDeclaration;
import org.jfxcore.compiler.util.Bytecode;
import org.jfxcore.compiler.util.CompilationContext;
import org.jfxcore.compiler.util.Local;
import org.jfxcore.compiler.util.PropertyInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.jfxcore.compiler.type.KnownSymbols.*;

/**
 * Emits code to establish a binding between the value that is currently on top of the
 * operand stack and the provided child value.
 */
public class EmitPropertyBindingNode extends AbstractNode implements EmitterNode, GeneratorEmitterNode {

    private final PropertyInfo propertyInfo;
    private final BindingMode bindingMode;
    private final ClassGenerator pushListenerGenerator;
    private ValueEmitterNode child;
    private ValueEmitterNode converter;
    private ValueEmitterNode format;

    public EmitPropertyBindingNode(
            PropertyInfo propertyInfo,
            BindingMode bindingMode,
            ValueEmitterNode child,
            @Nullable ValueEmitterNode converter,
            @Nullable ValueEmitterNode format,
            SourceInfo sourceInfo) {
        super(sourceInfo);
        this.propertyInfo = checkNotNull(propertyInfo);
        this.bindingMode = bindingMode;
        this.child = checkNotNull(child);
        this.converter = converter;
        this.format = format;

        if (bindingMode != BindingMode.BIDIRECTIONAL && (converter != null || format != null)) {
            throw new IllegalArgumentException();
        }

        if (converter != null && format != null) {
            throw new IllegalArgumentException();
        }

        pushListenerGenerator = bindingMode == BindingMode.REVERSE
            ? new PushListenerGenerator(propertyInfo.getType().declaration())
            : null;
    }

    public boolean isBidirectional() {
        return bindingMode.isBidirectional();
    }

    @Override
    public List<? extends Generator> emitGenerators(BytecodeEmitContext context) {
        List<Generator> generators = new ArrayList<>(3);

        if (pushListenerGenerator != null) {
            generators.add(pushListenerGenerator);
        }

        if (child instanceof EmitCollectionWrapperNode && bindingMode.isContent()) {
            generators.add(new ReferenceTrackerGenerator());
        }

        if (child.getNodeData(NodeDataKey.BIND_BIDIRECTIONAL_INVERT_BOOLEAN) == Boolean.TRUE
                && !CompilationContext.getCurrent().useSharedImplementation()) {
            generators.add(new InvertBooleanBindingGenerator());
        }

        return generators;
    }

    @Override
    public void emit(BytecodeEmitContext context) {
        Bytecode code = context.getOutput();

        if (child instanceof EmitCollectionWrapperNode && bindingMode.isContent()) {
            emitBindContentWrapper(context);
        } else {
            context.emit(child);

            Local source = code.acquireLocal(false);
            code.astore(source);

            if (NullableInfo.isNullable(child, true)) {
                code.aload(source)
                    .ifnonnull(() -> emitBinding(context, source));
            } else {
                emitBinding(context, source);
            }

            code.releaseLocal(source);
        }
    }

    private void emitBinding(BytecodeEmitContext context, Local source) {
        switch (bindingMode) {
            case UNIDIRECTIONAL -> emitBindUnidirectional(context, source);
            case BIDIRECTIONAL -> emitBindBidirectional(context, source);
            case REVERSE -> emitBindReverse(context, source);
            case UNIDIRECTIONAL_CONTENT,
                 BIDIRECTIONAL_CONTENT,
                 REVERSE_CONTENT -> emitBindContent(context, source);
            default -> throw new IllegalArgumentException(bindingMode.name());
        }
    }

    private void emitBindUnidirectional(BytecodeEmitContext context, Local source) {
        context.getOutput()
            .dup()
            .invoke(checkNotNull(propertyInfo.getPropertyGetter()))
            .aload(source)
            .invoke(PropertyDecl().requireDeclaredMethod("bind", ObservableValueDecl()));
    }

    private void emitBindBidirectional(BytecodeEmitContext context, Local source) {
        Bytecode code = context.getOutput();
        Local param2 = null;

        if (converter != null) {
            param2 = code.acquireLocal(false);
            converter.emit(context);
            code.astore(param2);
        } else if (format != null) {
            param2 = code.acquireLocal(false);
            format.emit(context);
            code.astore(param2);
        }

        code.dup()
            .invoke(checkNotNull(propertyInfo.getPropertyGetter()))
            .aload(source);

        if (param2 != null) {
            code.aload(param2);
        }

        if (child.getNodeData(NodeDataKey.BIND_BIDIRECTIONAL_INVERT_BOOLEAN) == Boolean.TRUE) {
            if (CompilationContext.getCurrent().useSharedImplementation()) {
                code.invoke(Markup.Runtime.BooleanBindingsDecl()
                                          .requireDeclaredMethod("bindBidirectionalComplement", PropertyDecl(), PropertyDecl()));
            } else {
                code.invoke(context.getNestedClasses()
                                   .find(InvertBooleanBindingGenerator.CLASS_NAME)
                                   .requireDeclaredMethod("bindBidirectional", PropertyDecl(), PropertyDecl()));
            }
        } else if (converter != null) {
            code.invoke(StringPropertyDecl().requireDeclaredMethod("bindBidirectional", PropertyDecl(), StringConverterDecl()));
        } else if (format != null) {
            code.invoke(StringPropertyDecl().requireDeclaredMethod("bindBidirectional", PropertyDecl(), FormatDecl()));
        } else {
            code.invoke(PropertyDecl().requireDeclaredMethod("bindBidirectional", PropertyDecl()));
        }

        if (param2 != null) {
            code.releaseLocal(param2);
        }
    }

    private void emitBindReverse(BytecodeEmitContext context, Local source) {
        Bytecode code = context.getOutput();
        TypeDeclaration pushListenerDecl = pushListenerGenerator.getGeneratedClass();
        Local target = code.acquireLocal(false);
        Resolver resolver = new Resolver(getSourceInfo());
        TypeDeclaration observableTypeDecl = propertyInfo.getObservableType().declaration();
        TypeDeclaration targetDecl = resolver.getObservableClass(observableTypeDecl, false);
        TypeDeclaration sourceDecl = resolver.getObservableClass(observableTypeDecl, true);

        code.dup()
            .invoke(checkNotNull(propertyInfo.getPropertyGetter()))
            .astore(target)
            .aload(target)
            .anew(pushListenerDecl)
            .dup()
            .aload(target)
            .aload(source)
            .invoke(pushListenerDecl.requireDeclaredConstructor(targetDecl, sourceDecl))
            .invoke(ObservableDecl().requireDeclaredMethod("addListener", InvalidationListenerDecl()))
            .releaseLocal(target);
    }

    private void emitBindContent(BytecodeEmitContext context, Local source) {
        Bytecode code = context.getOutput();
        Local target = code.acquireLocal(false);

        code.dup()
            .invoke(propertyInfo.getPropertyGetterOrGetter())
            .astore(target);

        if (bindingMode.isReverse()) {
            code.aload(source)
                .aload(target);
        } else {
            code.aload(target)
                .aload(source);
        }

        code.invoke(bindContentMethod())
            .releaseLocal(target);
    }

    private void emitBindContentWrapper(BytecodeEmitContext context) {
        Bytecode code = context.getOutput();
        Local source = code.acquireLocal(false);
        Local target = code.acquireLocal(false);

        code.dup()
            .invoke(propertyInfo.getPropertyGetterOrGetter())
            .astore(target)
            .aload(target);

        child.emit(context);

        code.astore(source);

        if (bindingMode.isReverse()) {
            code.aload(source)
                .aload(target);
        } else {
            code.aload(target)
                .aload(source);
        }

        code.invoke(bindContentMethod())
            .aload(0)
            .aload(target)
            .aload(source)
            .invoke(context.getMarkupClass()
                           .requireDeclaredMethod(ReferenceTrackerGenerator.ADD_REFERENCE_METHOD,
                                                  ObjectDecl(), ObjectDecl()));

        code.releaseLocal(target);
        code.releaseLocal(source);
    }

    private BehaviorDeclaration bindContentMethod() {
        String methodName = switch (bindingMode) {
            case UNIDIRECTIONAL_CONTENT, REVERSE_CONTENT -> "bindContent";
            case BIDIRECTIONAL_CONTENT -> "bindContentBidirectional";
            default -> throw new IllegalArgumentException(bindingMode.name());
        };

        boolean bidirectional = bindingMode.isBidirectional();

        if (propertyInfo.getType().subtypeOf(ListDecl())) {
            return BindingsDecl().requireDeclaredMethod(
                methodName, bidirectional ? ObservableListDecl() : ListDecl(), ObservableListDecl());
        }

        if (propertyInfo.getType().subtypeOf(SetDecl())) {
            return BindingsDecl().requireDeclaredMethod(
                methodName, bidirectional ? ObservableSetDecl() : SetDecl(), ObservableSetDecl());
        }

        if (propertyInfo.getType().subtypeOf(MapDecl())) {
            return BindingsDecl().requireDeclaredMethod(
                methodName, bidirectional ? ObservableMapDecl() : MapDecl(), ObservableMapDecl());
        }

        throw new IllegalArgumentException(propertyInfo.getType().toString());
    }

    @Override
    public void acceptChildren(Visitor visitor) {
        super.acceptChildren(visitor);
        child = (ValueEmitterNode)child.accept(visitor);

        if (converter != null) {
            converter = (ValueEmitterNode)converter.accept(visitor);
        }

        if (format != null) {
            format = (ValueEmitterNode)format.accept(visitor);
        }
    }

    @Override
    public EmitPropertyBindingNode deepClone() {
        return new EmitPropertyBindingNode(
            propertyInfo,
            bindingMode,
            child.deepClone(),
            converter != null ? converter.deepClone() : null,
            format != null ? format.deepClone() : null,
            getSourceInfo()).copy(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmitPropertyBindingNode that = (EmitPropertyBindingNode)o;
        return bindingMode == that.bindingMode &&
            propertyInfo.equals(that.propertyInfo) &&
            child.equals(that.child) &&
            Objects.equals(converter, that.converter) &&
            Objects.equals(format, that.format);
    }

    @Override
    public int hashCode() {
        return Objects.hash(propertyInfo, bindingMode, child, converter, format);
    }
}
