// Copyright (c) 2021, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.util;

import org.jfxcore.compiler.diagnostic.SourceInfo;
import javassist.CtClass;
import java.util.HashMap;
import java.util.Map;

public final class Classes {

    private Classes() {}

    public static final String ClassName = "java.lang.Class";
    public static final String ObjectName = "java.lang.Object";
    public static final String StringName = "java.lang.String";
    public static final String BooleanName = "java.lang.Boolean";
    public static final String CharacterName = "java.lang.Character";
    public static final String ByteName = "java.lang.Byte";
    public static final String ShortName = "java.lang.Short";
    public static final String IntegerName = "java.lang.Integer";
    public static final String LongName = "java.lang.Long";
    public static final String FloatName = "java.lang.Float";
    public static final String DoubleName = "java.lang.Double";
    public static final String NumberName = "java.lang.Number";

    public static final String DefaultPropertyAnnotationName = "javafx.beans.DefaultProperty";
    public static final String NamedArgAnnotationName = "javafx.beans.NamedArg";
    public static final String InverseMethodAnnotationName = "javafx.fxml.InverseMethod";

    public static CtClass ObjectType() { return get("java.lang.Object"); }
    public static CtClass ObjectsType() { return get("java.util.Objects"); }
    public static CtClass ClassType() { return get("java.lang.Class"); }
    public static CtClass StringType() { return get("java.lang.String"); }
    public static CtClass BooleanType() { return get("java.lang.Boolean"); }
    public static CtClass ByteType() { return get("java.lang.Byte"); }
    public static CtClass CharacterType() { return get("java.lang.Character"); }
    public static CtClass ShortType() { return get("java.lang.Short"); }
    public static CtClass IntegerType() { return get("java.lang.Integer"); }
    public static CtClass LongType() { return get("java.lang.Long"); }
    public static CtClass FloatType() { return get("java.lang.Float"); }
    public static CtClass DoubleType() { return get("java.lang.Double"); }
    public static CtClass NumberType() { return get("java.lang.Number"); }
    public static CtClass URLType() { return get("java.net.URL"); }
    public static CtClass URIType() { return get("java.net.URI"); }
    public static CtClass ThreadType() { return get("java.lang.Thread"); }
    public static CtClass UncaughtExceptionHandlerType() { return get("java.lang.Thread.UncaughtExceptionHandler"); }
    public static CtClass ThrowableType() { return get("java.lang.Throwable"); }
    public static CtClass RuntimeExceptionType() { return get("java.lang.RuntimeException"); }
    public static CtClass CollectionType() { return get("java.util.Collection"); }
    public static CtClass FXCollectionsType() { return get("javafx.collections.FXCollections"); }
    public static CtClass ObservableType() { return get("javafx.beans.Observable"); }
    public static CtClass ObservableValueType() { return get("javafx.beans.value.ObservableValue"); }
    public static CtClass ObservableBooleanValueType() { return get("javafx.beans.value.ObservableBooleanValue"); }
    public static CtClass ObservableIntegerValueType() { return get("javafx.beans.value.ObservableIntegerValue"); }
    public static CtClass ObservableLongValueType() { return get("javafx.beans.value.ObservableLongValue"); }
    public static CtClass ObservableFloatValueType() { return get("javafx.beans.value.ObservableFloatValue"); }
    public static CtClass ObservableDoubleValueType() { return get("javafx.beans.value.ObservableDoubleValue"); }
    public static CtClass ObservableNumberValueType() { return get("javafx.beans.value.ObservableNumberValue"); }
    public static CtClass ObservableObjectValueType() { return get("javafx.beans.value.ObservableObjectValue"); }
    public static CtClass ObservableListValueType() { return get("javafx.beans.value.ObservableListValue"); }
    public static CtClass ObservableSetValueType() { return get("javafx.beans.value.ObservableSetValue"); }
    public static CtClass ObservableMapValueType() { return get("javafx.beans.value.ObservableMapValue"); }
    public static CtClass WritableValueType() { return get("javafx.beans.value.WritableValue"); }
    public static CtClass WritableBooleanValueType() { return get("javafx.beans.value.WritableBooleanValue"); }
    public static CtClass WritableIntegerValueType() { return get("javafx.beans.value.WritableIntegerValue"); }
    public static CtClass WritableLongValueType() { return get("javafx.beans.value.WritableLongValue"); }
    public static CtClass WritableFloatValueType() { return get("javafx.beans.value.WritableFloatValue"); }
    public static CtClass WritableDoubleValueType() { return get("javafx.beans.value.WritableDoubleValue"); }
    public static CtClass PropertyType() { return get("javafx.beans.property.Property"); }
    public static CtClass BooleanPropertyType() { return get("javafx.beans.property.BooleanProperty"); }
    public static CtClass IntegerPropertyType() { return get("javafx.beans.property.IntegerProperty"); }
    public static CtClass LongPropertyType() { return get("javafx.beans.property.LongProperty"); }
    public static CtClass FloatPropertyType() { return get("javafx.beans.property.FloatProperty"); }
    public static CtClass DoublePropertyType() { return get("javafx.beans.property.DoubleProperty"); }
    public static CtClass ReadOnlyListPropertyType() { return get("javafx.beans.property.ReadOnlyListProperty"); }
    public static CtClass ReadOnlySetPropertyType() { return get("javafx.beans.property.ReadOnlySetProperty"); }
    public static CtClass ReadOnlyMapPropertyType() { return get("javafx.beans.property.ReadOnlyMapProperty"); }
    public static CtClass BindingsType() { return get("javafx.beans.binding.Bindings"); }
    public static CtClass BooleanBindingType() { return get("javafx.beans.binding.BooleanBinding"); }
    public static CtClass ListType() { return get("java.util.List"); }
    public static CtClass SetType() { return get("java.util.Set"); }
    public static CtClass MapType() { return get("java.util.Map"); }
    public static CtClass ObservableListType() { return get("javafx.collections.ObservableList"); }
    public static CtClass ObservableSetType() { return get("javafx.collections.ObservableSet"); }
    public static CtClass ObservableMapType() { return get("javafx.collections.ObservableMap"); }
    public static CtClass InvalidationListenerType() { return get("javafx.beans.InvalidationListener"); }
    public static CtClass ChangeListenerType() { return get("javafx.beans.value.ChangeListener"); }
    public static CtClass EventTypent() { return get("javafx.event.Event"); }
    public static CtClass EventHandlerType() { return get("javafx.event.EventHandler"); }
    public static CtClass ColorType() { return get("javafx.scene.paint.Color"); }
    public static CtClass NodeType() { return get("javafx.scene.Node"); }
    public static CtClass ParentType() { return get("javafx.scene.Parent"); }

    public static final class Core {
        private Core() {}
        public static CtClass TemplateType() { return getOptional("javafx.scene.control.template.Template"); }
        public static CtClass TemplateContentType() { return getOptional("javafx.scene.control.template.TemplateContent"); }
    }

    private static CtClass getOptional(String name) {
        CtClass clazz = getClassCache().get(name);
        if (clazz == null) {
            clazz = new Resolver(new SourceInfo(0, 0)).tryResolveClass(name);
            getClassCache().put(name, clazz);
        }

        return clazz;
    }

    private static CtClass get(String name) {
        CtClass clazz = getOptional(name);
        if (clazz == null) {
            throw new RuntimeException("Class not found: " + name);
        }

        return clazz;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, CtClass> getClassCache() {
        return (Map<String, CtClass>)CompilationContext.getCurrent()
            .computeIfAbsent(Classes.class, key -> new HashMap<String, CtClass>());
    }

}
