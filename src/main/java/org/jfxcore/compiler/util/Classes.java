// Copyright (c) 2021, 2025, JFXcore. All rights reserved.
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

    private static final CtClass nullType = new CtClass("<null>") {
        @Override
        public boolean subtypeOf(CtClass clazz) {
            return !clazz.isPrimitive();
        }

        @Override
        public boolean subclassOf(CtClass superclass) {
            return !superclass.isPrimitive();
        }
    };

    private static final CtClass bottomType = new CtClass("<bottom>") {
        @Override
        public boolean subtypeOf(CtClass clazz) {
            return true;
        }

        @Override
        public boolean subclassOf(CtClass superclass) {
            return true;
        }
    };

    public static CtClass NullType() { return nullType; }
    public static CtClass BottomType() { return bottomType; }

    // java.lang
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
    public static CtClass ThreadType() { return get("java.lang.Thread"); }
    public static CtClass UncaughtExceptionHandlerType() { return get("java.lang.Thread.UncaughtExceptionHandler"); }
    public static CtClass ThrowableType() { return get("java.lang.Throwable"); }
    public static CtClass RuntimeExceptionType() { return get("java.lang.RuntimeException"); }
    public static CtClass IllegalStateExceptionType() { return get("java.lang.IllegalStateException"); }
    public static CtClass UnsupportedOperationExceptionType() { return get("java.lang.UnsupportedOperationException"); }

    // java.lang.ref
    public static CtClass ReferenceType() { return get("java.lang.ref.Reference"); }
    public static CtClass WeakReferenceType() { return get("java.lang.ref.WeakReference"); }
    public static CtClass ReferenceQueueType() { return get("java.lang.ref.ReferenceQueue"); }

    // java.text
    public static CtClass FormatType() { return get("java.text.Format"); }

    // java.util
    public static CtClass CollectionType() { return get("java.util.Collection"); }
    public static CtClass CollectionsType() { return get("java.util.Collections"); }
    public static CtClass IteratorType() { return get("java.util.Iterator"); }
    public static CtClass ListIteratorType() { return get("java.util.ListIterator"); }
    public static CtClass ListType() { return get("java.util.List"); }
    public static CtClass SetType() { return get("java.util.Set"); }
    public static CtClass HashSetType() { return get("java.util.HashSet"); }
    public static CtClass MapType() { return get("java.util.Map"); }
    public static CtClass MapEntryType() { return get("java.util.Map.Entry"); }

    // java.net
    public static CtClass URLType() { return get("java.net.URL"); }
    public static CtClass URIType() { return get("java.net.URI"); }

    // javafx.beans
    public static CtClass ObservableType() { return get("javafx.beans.Observable"); }
    public static CtClass InvalidationListenerType() { return get("javafx.beans.InvalidationListener"); }
    public static CtClass WeakInvalidationListenerType() { return get("javafx.beans.WeakInvalidationListener"); }
    public static CtClass WeakListenerType() { return get("javafx.beans.WeakListener"); }

    // javafx.beans.value
    public static CtClass ObservableValueType() { return get("javafx.beans.value.ObservableValue"); }
    public static CtClass ChangeListenerType() { return get("javafx.beans.value.ChangeListener"); }
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

    // javafx.beans.property
    public static CtClass PropertyType() { return get("javafx.beans.property.Property"); }
    public static CtClass BooleanPropertyType() { return get("javafx.beans.property.BooleanProperty"); }
    public static CtClass IntegerPropertyType() { return get("javafx.beans.property.IntegerProperty"); }
    public static CtClass LongPropertyType() { return get("javafx.beans.property.LongProperty"); }
    public static CtClass FloatPropertyType() { return get("javafx.beans.property.FloatProperty"); }
    public static CtClass DoublePropertyType() { return get("javafx.beans.property.DoubleProperty"); }
    public static CtClass StringPropertyType() { return get("javafx.beans.property.StringProperty"); }
    public static CtClass ReadOnlyListPropertyType() { return get("javafx.beans.property.ReadOnlyListProperty"); }
    public static CtClass ReadOnlySetPropertyType() { return get("javafx.beans.property.ReadOnlySetProperty"); }
    public static CtClass ReadOnlyMapPropertyType() { return get("javafx.beans.property.ReadOnlyMapProperty"); }

    // javafx.beans.binding
    public static CtClass BindingsType() { return get("javafx.beans.binding.Bindings"); }
    public static CtClass BooleanBindingType() { return get("javafx.beans.binding.BooleanBinding"); }

    // javafx.collections
    public static CtClass FXCollectionsType() { return get("javafx.collections.FXCollections"); }
    public static CtClass ObservableListType() { return get("javafx.collections.ObservableList"); }
    public static CtClass ObservableSetType() { return get("javafx.collections.ObservableSet"); }
    public static CtClass ObservableMapType() { return get("javafx.collections.ObservableMap"); }
    public static CtClass ListChangeListenerType() { return get("javafx.collections.ListChangeListener"); }
    public static CtClass ListChangeListenerChangeType() { return get("javafx.collections.ListChangeListener.Change"); }
    public static CtClass WeakListChangeListenerType() { return get("javafx.collections.WeakListChangeListener"); }
    public static CtClass SetChangeListenerType() { return get("javafx.collections.SetChangeListener"); }
    public static CtClass SetChangeListenerChangeType() { return get("javafx.collections.SetChangeListener.Change"); }
    public static CtClass WeakSetChangeListenerType() { return get("javafx.collections.WeakSetChangeListener"); }
    public static CtClass MapChangeListenerType() { return get("javafx.collections.MapChangeListener"); }
    public static CtClass MapChangeListenerChangeType() { return get("javafx.collections.MapChangeListener.Change"); }
    public static CtClass WeakMapChangeListenerType() { return get("javafx.collections.WeakMapChangeListener"); }

    // javafx.event
    public static CtClass EventType() { return get("javafx.event.Event"); }
    public static CtClass EventHandlerType() { return get("javafx.event.EventHandler"); }

    // javafx.scene
    public static CtClass NodeType() { return get("javafx.scene.Node"); }
    public static CtClass ParentType() { return get("javafx.scene.Parent"); }
    public static CtClass ColorType() { return get("javafx.scene.paint.Color"); }

    // javafx.util
    public static CtClass StringConverterType() { return get("javafx.util.StringConverter"); }

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
