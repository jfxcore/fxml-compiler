// Copyright (c) 2022, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.util;

import javafx.beans.property.Property;
import javafx.beans.value.ObservableValue;
import javafx.beans.value.WritableValue;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;
import org.jetbrains.annotations.Nullable;
import org.jfxcore.compiler.ast.BindingMode;
import java.util.Objects;

import static org.jfxcore.compiler.util.Classes.*;

/**
 * The compiler will recognize a JavaFX property if it is implemented by a well-defined set of methods.
 *
 * <p>Read-only properties:
 * <ol>
 *     <li>T getValue()
 *     <li>ObservableValue&lt;T&gt; valueProperty()
 *     <li>ObservableValue&lt;T&gt; valueProperty(), T getValue()
 *     <li>static T getValue(Node)
 *     <li>static ObservableValue&lt;T&gt; valueProperty(Node)
 *     <li>static ObservableValue&lt;T&gt; valueProperty(Node), static T getValue(Node)
 * </ol>
 *
 * <p>Writable properties:
 * <ol>
 *     <li>T getValue(), void setValue(T)
 *     <li>Property&lt;T&gt; valueProperty()
 *     <li>Property&lt;T&gt; valueProperty(), T getValue()
 *     <li>Property&lt;T&gt; valueProperty(), T getValue(), void setValue(T)
 *     <li>static T getValue(Node), static void setValue(Node, T)
 *     <li>static Property&lt;T&gt; valueProperty(Node)
 *     <li>static Property&lt;T&gt; valueProperty(Node), T getValue(Node)
 *     <li>static Property&lt;T&gt; valueProperty(Node), T getValue(Node), void setValue(Node, T)
 * </ol>
 *
 * <p>If the property uses a primitive wrapper like ObservableBooleanValue, the optional getter and setter
 * methods will only be recognized if they return or set a primitive value (i.e. not a boxed value).
 */
public class PropertyInfo {

    private final String name;
    private final CtMethod propertyGetter;
    private final CtMethod getter;
    private final CtMethod setter;
    private final TypeInstance typeInstance;
    private final TypeInstance observableType;
    private final TypeInstance declaringType;
    private final boolean isStatic;
    private final boolean observable;
    private final boolean readonly;
    private final boolean bindable;

    PropertyInfo(
            String name,
            CtMethod propertyGetter,
            CtMethod getter,
            CtMethod setter,
            TypeInstance typeInstance,
            TypeInstance observableType,
            TypeInstance declaringType,
            boolean isStatic) throws NotFoundException {
        this.name = name;
        this.propertyGetter = propertyGetter;
        this.getter = getter;
        this.setter = setter;
        this.typeInstance = typeInstance;
        this.observableType = observableType;
        this.declaringType = declaringType;
        this.isStatic = isStatic;
        this.observable = propertyGetter != null;
        this.readonly = setter == null &&
            (propertyGetter == null || !propertyGetter.getReturnType().subtypeOf(Classes.WritableValueType()));
        this.bindable = propertyGetter != null && propertyGetter.getReturnType().subtypeOf(Classes.PropertyType());
    }

    public String getName() {
        return name;
    }

    /**
     * Returns the property getter method.
     * This may be {@code null} if the property doesn't have an {@link ObservableValue}-based property getter.
     */
    public @Nullable CtMethod getPropertyGetter() {
        return propertyGetter;
    }

    /**
     * Returns the getter method.
     * This may be {@code null} if the property doesn't have a getter method.
     * If there is no getter method, the property must have an {@link ObservableValue}-based property getter.
     */
    public @Nullable CtMethod getGetter() {
        return getter;
    }

    /**
     * Returns the setter method. This may be {@code null} if the property doesn't have a setter method.
     */
    public @Nullable CtMethod getSetter() {
        return setter;
    }

    /**
     * Returns the property getter method. If there is no property getter method, returns the getter method.
     */
    public CtMethod getPropertyGetterOrGetter() {
        return propertyGetter != null ? propertyGetter : getter;
    }

    /**
     * Returns the getter method. If there is no getter method, returns the property getter method.
     */
    public CtMethod getGetterOrPropertyGetter() {
        return getter != null ? getter : propertyGetter;
    }

    /**
     * Returns the setter method. If there is no setter method, returns the property getter method.
     */
    public CtMethod getSetterOrPropertyGetter() {
        return setter != null ? setter : propertyGetter;
    }

    /**
     * Returns the value type of the property.
     * For properties that use primitive wrappers like ObservableBooleanValue, returns the primitive type.
     */
    public TypeInstance getValueTypeInstance() {
        return typeInstance;
    }

    /**
     * Returns the value type of the property.
     * For properties that use primitive wrappers like ObservableBooleanValue, returns the primitive type.
     */
    public CtClass getValueType() {
        return typeInstance.jvmType();
    }

    /**
     * Returns the observable type of the property.
     */
    public TypeInstance getObservableTypeInstance() {
        return observableType;
    }

    /**
     * Returns the declaring type of the property.
     */
    public CtClass getDeclaringType() {
        return declaringType.jvmType();
    }

    /**
     * Returns the declaring type of the property.
     */
    public TypeInstance getDeclaringTypeInstance() {
        return declaringType;
    }

    /**
     * Returns whether the property is a static property.
     */
    public boolean isStatic() {
        return isStatic;
    }

    /**
     * Returns whether the property has an {@link ObservableValue}-based property getter.
     */
    public boolean isObservable() {
        return observable;
    }

    /**
     * Returns whether the property has no setter and no {@link WritableValue}-based property getter.
     */
    public boolean isReadOnly() {
        return readonly;
    }

    /**
     * Returns whether the property has a {@link Property}-based property getter, which can be used to set up bindings.
     */
    public boolean isBindable() {
        return bindable;
    }

    /**
     * Returns whether the property is content-bindable using one of
     * {@link java.util.Collection#addAll},
     * {@link java.util.Map#putAll},
     * {@link javafx.beans.binding.Bindings#bindContent}, or
     * {@link javafx.beans.binding.Bindings#bindContentBidirectional}.
     */
    public boolean isContentBindable(BindingMode forMode) {
        switch (forMode) {
            case CONTENT:
                return typeInstance.subtypeOf(CollectionType())
                    || typeInstance.subtypeOf(MapType());

            case UNIDIRECTIONAL_CONTENT:
                return (observableType == null || observableType.subtypeOf(ListType())) && typeInstance.subtypeOf(ListType())
                    || (observableType == null || observableType.subtypeOf(SetType())) && typeInstance.subtypeOf(SetType())
                    || (observableType == null || observableType.subtypeOf(MapType())) && typeInstance.subtypeOf(MapType());

            case BIDIRECTIONAL_CONTENT:
                return (observableType == null || observableType.subtypeOf(ObservableListType())) && typeInstance.subtypeOf(ObservableListType())
                    || (observableType == null || observableType.subtypeOf(ObservableSetType())) && typeInstance.subtypeOf(ObservableSetType())
                    || (observableType == null || observableType.subtypeOf(ObservableMapType())) && typeInstance.subtypeOf(ObservableMapType());

            default:
                return false;
        }
    }

    @Override
    public String toString() {
        return declaringType.getSimpleName() + "." + name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PropertyInfo that = (PropertyInfo)o;
        return name.equals(that.name) &&
            declaringType.equals(that.declaringType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, declaringType);
    }

}
