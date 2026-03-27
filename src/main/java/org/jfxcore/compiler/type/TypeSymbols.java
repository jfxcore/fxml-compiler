// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler.type;

import javassist.CtClass;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.util.CompilationContext;
import java.util.HashMap;
import java.util.Map;

public final class TypeSymbols {

    private TypeSymbols() {}

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

    public static final String IDPropertyAnnotationName = "com.sun.javafx.beans.IDProperty";
    public static final String DefaultPropertyAnnotationName = "javafx.beans.DefaultProperty";
    public static final String NamedArgAnnotationName = "javafx.beans.NamedArg";

    private static final TypeDeclaration nullType = new TypeDeclaration(new CtClass("<null>") {
        @Override
        public boolean subtypeOf(CtClass clazz) {
            return !clazz.isPrimitive();
        }

        @Override
        public boolean subclassOf(CtClass superclass) {
            return !superclass.isPrimitive();
        }
    });

    private static final TypeDeclaration bottomType = new TypeDeclaration(new CtClass("<bottom>") {
        @Override
        public boolean subtypeOf(CtClass clazz) {
            return true;
        }

        @Override
        public boolean subclassOf(CtClass superclass) {
            return true;
        }
    });

    public static TypeDeclaration NullTypeDecl() { return nullType; }
    public static TypeDeclaration BottomTypeDecl() { return bottomType; }

    // Primitives
    public static TypeDeclaration voidDecl() { return TypeDeclaration.of(CtClass.voidType); }
    public static TypeDeclaration booleanDecl() { return TypeDeclaration.of(CtClass.booleanType); }
    public static TypeDeclaration charDecl() { return TypeDeclaration.of(CtClass.charType); }
    public static TypeDeclaration byteDecl() { return TypeDeclaration.of(CtClass.byteType); }
    public static TypeDeclaration shortDecl() { return TypeDeclaration.of(CtClass.shortType); }
    public static TypeDeclaration intDecl() { return TypeDeclaration.of(CtClass.intType); }
    public static TypeDeclaration longDecl() { return TypeDeclaration.of(CtClass.longType); }
    public static TypeDeclaration floatDecl() { return TypeDeclaration.of(CtClass.floatType); }
    public static TypeDeclaration doubleDecl() { return TypeDeclaration.of(CtClass.doubleType); }

    // java.io
    public static TypeDeclaration SerializableDecl() { return get("java.io.Serializable"); }

    // java.lang
    public static TypeDeclaration SystemDecl() { return get("java.lang.System"); }
    public static TypeDeclaration ObjectDecl() { return get("java.lang.Object"); }
    public static TypeDeclaration CloneableDecl() { return get("java.lang.Cloneable"); }
    public static TypeDeclaration ObjectsDecl() { return get("java.util.Objects"); }
    public static TypeDeclaration ClassDecl() { return get("java.lang.Class"); }
    public static TypeDeclaration StringDecl() { return get("java.lang.String"); }
    public static TypeDeclaration BooleanDecl() { return get("java.lang.Boolean"); }
    public static TypeDeclaration ByteDecl() { return get("java.lang.Byte"); }
    public static TypeDeclaration CharacterDecl() { return get("java.lang.Character"); }
    public static TypeDeclaration ShortDecl() { return get("java.lang.Short"); }
    public static TypeDeclaration IntegerDecl() { return get("java.lang.Integer"); }
    public static TypeDeclaration LongDecl() { return get("java.lang.Long"); }
    public static TypeDeclaration FloatDecl() { return get("java.lang.Float"); }
    public static TypeDeclaration DoubleDecl() { return get("java.lang.Double"); }
    public static TypeDeclaration NumberDecl() { return get("java.lang.Number"); }
    public static TypeDeclaration ThreadDecl() { return get("java.lang.Thread"); }
    public static TypeDeclaration UncaughtExceptionHandlerDecl() { return get("java.lang.Thread.UncaughtExceptionHandler"); }
    public static TypeDeclaration ThrowableDecl() { return get("java.lang.Throwable"); }
    public static TypeDeclaration RuntimeExceptionDecl() { return get("java.lang.RuntimeException"); }
    public static TypeDeclaration IllegalStateExceptionDecl() { return get("java.lang.IllegalStateException"); }
    public static TypeDeclaration UnsupportedOperationExceptionDecl() { return get("java.lang.UnsupportedOperationException"); }

    // java.lang.ref
    public static TypeDeclaration ReferenceDecl() { return get("java.lang.ref.Reference"); }
    public static TypeDeclaration WeakReferenceDecl() { return get("java.lang.ref.WeakReference"); }
    public static TypeDeclaration ReferenceQueueDecl() { return get("java.lang.ref.ReferenceQueue"); }

    // java.text
    public static TypeDeclaration FormatDecl() { return get("java.text.Format"); }

    // java.util
    public static TypeDeclaration CollectionDecl() { return get("java.util.Collection"); }
    public static TypeDeclaration CollectionsDecl() { return get("java.util.Collections"); }
    public static TypeDeclaration IteratorDecl() { return get("java.util.Iterator"); }
    public static TypeDeclaration ListIteratorDecl() { return get("java.util.ListIterator"); }
    public static TypeDeclaration ListDecl() { return get("java.util.List"); }
    public static TypeDeclaration SetDecl() { return get("java.util.Set"); }
    public static TypeDeclaration HashSetDecl() { return get("java.util.HashSet"); }
    public static TypeDeclaration MapDecl() { return get("java.util.Map"); }
    public static TypeDeclaration MapEntryDecl() { return get("java.util.Map.Entry"); }

    // javafx.beans
    public static TypeDeclaration ObservableDecl() { return get("javafx.beans.Observable"); }
    public static TypeDeclaration InvalidationListenerDecl() { return get("javafx.beans.InvalidationListener"); }
    public static TypeDeclaration WeakInvalidationListenerDecl() { return get("javafx.beans.WeakInvalidationListener"); }
    public static TypeDeclaration WeakListenerDecl() { return get("javafx.beans.WeakListener"); }

    // javafx.beans.value
    public static TypeDeclaration ObservableValueDecl() { return get("javafx.beans.value.ObservableValue"); }
    public static TypeDeclaration ChangeListenerDecl() { return get("javafx.beans.value.ChangeListener"); }
    public static TypeDeclaration ObservableBooleanValueDecl() { return get("javafx.beans.value.ObservableBooleanValue"); }
    public static TypeDeclaration ObservableIntegerValueDecl() { return get("javafx.beans.value.ObservableIntegerValue"); }
    public static TypeDeclaration ObservableLongValueDecl() { return get("javafx.beans.value.ObservableLongValue"); }
    public static TypeDeclaration ObservableFloatValueDecl() { return get("javafx.beans.value.ObservableFloatValue"); }
    public static TypeDeclaration ObservableDoubleValueDecl() { return get("javafx.beans.value.ObservableDoubleValue"); }
    public static TypeDeclaration ObservableNumberValueDecl() { return get("javafx.beans.value.ObservableNumberValue"); }
    public static TypeDeclaration ObservableObjectValueDecl() { return get("javafx.beans.value.ObservableObjectValue"); }
    public static TypeDeclaration ObservableListValueDecl() { return get("javafx.beans.value.ObservableListValue"); }
    public static TypeDeclaration ObservableSetValueDecl() { return get("javafx.beans.value.ObservableSetValue"); }
    public static TypeDeclaration ObservableMapValueDecl() { return get("javafx.beans.value.ObservableMapValue"); }
    public static TypeDeclaration WritableValueDecl() { return get("javafx.beans.value.WritableValue"); }
    public static TypeDeclaration WritableBooleanValueDecl() { return get("javafx.beans.value.WritableBooleanValue"); }
    public static TypeDeclaration WritableIntegerValueDecl() { return get("javafx.beans.value.WritableIntegerValue"); }
    public static TypeDeclaration WritableLongValueDecl() { return get("javafx.beans.value.WritableLongValue"); }
    public static TypeDeclaration WritableFloatValueDecl() { return get("javafx.beans.value.WritableFloatValue"); }
    public static TypeDeclaration WritableDoubleValueDecl() { return get("javafx.beans.value.WritableDoubleValue"); }

    // javafx.beans.property
    public static TypeDeclaration PropertyDecl() { return get("javafx.beans.property.Property"); }
    public static TypeDeclaration BooleanPropertyDecl() { return get("javafx.beans.property.BooleanProperty"); }
    public static TypeDeclaration IntegerPropertyDecl() { return get("javafx.beans.property.IntegerProperty"); }
    public static TypeDeclaration LongPropertyDecl() { return get("javafx.beans.property.LongProperty"); }
    public static TypeDeclaration FloatPropertyDecl() { return get("javafx.beans.property.FloatProperty"); }
    public static TypeDeclaration DoublePropertyDecl() { return get("javafx.beans.property.DoubleProperty"); }
    public static TypeDeclaration StringPropertyDecl() { return get("javafx.beans.property.StringProperty"); }
    public static TypeDeclaration ReadOnlyPropertyDecl() { return get("javafx.beans.property.ReadOnlyProperty"); }
    public static TypeDeclaration ReadOnlyListPropertyDecl() { return get("javafx.beans.property.ReadOnlyListProperty"); }
    public static TypeDeclaration ReadOnlySetPropertyDecl() { return get("javafx.beans.property.ReadOnlySetProperty"); }
    public static TypeDeclaration ReadOnlyMapPropertyDecl() { return get("javafx.beans.property.ReadOnlyMapProperty"); }

    // javafx.beans.binding
    public static TypeDeclaration BindingsDecl() { return get("javafx.beans.binding.Bindings"); }
    public static TypeDeclaration BooleanBindingDecl() { return get("javafx.beans.binding.BooleanBinding"); }

    // javafx.collections
    public static TypeDeclaration FXCollectionsDecl() { return get("javafx.collections.FXCollections"); }
    public static TypeDeclaration ObservableListDecl() { return get("javafx.collections.ObservableList"); }
    public static TypeDeclaration ObservableSetDecl() { return get("javafx.collections.ObservableSet"); }
    public static TypeDeclaration ObservableMapDecl() { return get("javafx.collections.ObservableMap"); }
    public static TypeDeclaration ListChangeListenerDecl() { return get("javafx.collections.ListChangeListener"); }
    public static TypeDeclaration ListChangeListenerChangeDecl() { return get("javafx.collections.ListChangeListener.Change"); }
    public static TypeDeclaration WeakListChangeListenerDecl() { return get("javafx.collections.WeakListChangeListener"); }
    public static TypeDeclaration SetChangeListenerDecl() { return get("javafx.collections.SetChangeListener"); }
    public static TypeDeclaration SetChangeListenerChangeDecl() { return get("javafx.collections.SetChangeListener.Change"); }
    public static TypeDeclaration WeakSetChangeListenerDecl() { return get("javafx.collections.WeakSetChangeListener"); }
    public static TypeDeclaration MapChangeListenerDecl() { return get("javafx.collections.MapChangeListener"); }
    public static TypeDeclaration MapChangeListenerChangeDecl() { return get("javafx.collections.MapChangeListener.Change"); }
    public static TypeDeclaration WeakMapChangeListenerDecl() { return get("javafx.collections.WeakMapChangeListener"); }

    // javafx.event
    public static TypeDeclaration EventDecl() { return get("javafx.event.Event"); }
    public static TypeDeclaration EventHandlerDecl() { return get("javafx.event.EventHandler"); }

    // javafx.scene
    public static TypeDeclaration NodeDecl() { return get("javafx.scene.Node"); }
    public static TypeDeclaration ParentDecl() { return get("javafx.scene.Parent"); }
    public static TypeDeclaration ColorDecl() { return get("javafx.scene.paint.Color"); }

    // javafx.util
    public static TypeDeclaration StringConverterDecl() { return get("javafx.util.StringConverter"); }

    public static final class Core {
        private Core() {}
        public static TypeDeclaration TemplateDecl() { return getOptional("javafx.scene.control.template.Template"); }
        public static TypeDeclaration TemplateContentDecl() { return getOptional("javafx.scene.control.template.TemplateContent"); }
    }

    public static final class Markup {
        private Markup() {}
        public static final String InverseMethodAnnotationName = "org.jfxcore.markup.InverseMethod";
        public static final String MarkupExtensionReturnTypeAnnotationName = "org.jfxcore.markup.MarkupExtension$Supplier$ReturnType";

        public static boolean isAvailable() { return getOptional("org.jfxcore.markup.MarkupExtension") != null; }
        public static TypeDeclaration MarkupExtensionDecl() { return get("org.jfxcore.markup.MarkupExtension"); }
        public static TypeDeclaration MarkupContextDecl() { return get("org.jfxcore.markup.MarkupContext"); }

        public static final class MarkupExtension {
            private MarkupExtension() {}
            public static TypeDeclaration SupplierDecl() { return get("org.jfxcore.markup.MarkupExtension$Supplier"); }
            public static TypeDeclaration BooleanSupplierDecl() { return get("org.jfxcore.markup.MarkupExtension$BooleanSupplier"); }
            public static TypeDeclaration IntSupplierDecl() { return get("org.jfxcore.markup.MarkupExtension$IntSupplier"); }
            public static TypeDeclaration LongSupplierDecl() { return get("org.jfxcore.markup.MarkupExtension$LongSupplier"); }
            public static TypeDeclaration FloatSupplierDecl() { return get("org.jfxcore.markup.MarkupExtension$FloatSupplier"); }
            public static TypeDeclaration DoubleSupplierDecl() { return get("org.jfxcore.markup.MarkupExtension$DoubleSupplier"); }
            public static TypeDeclaration PropertyConsumerDecl() { return get("org.jfxcore.markup.MarkupExtension$PropertyConsumer"); }
            public static TypeDeclaration ReadOnlyPropertyConsumerDecl() { return get("org.jfxcore.markup.MarkupExtension$ReadOnlyPropertyConsumer"); }
        }

        public static final class Runtime {
            private Runtime() {}
            public static TypeDeclaration BooleanBindingsDecl() { return get("org.jfxcore.markup.runtime.BooleanBindings"); }
        }
    }
    
    private static TypeDeclaration getOptional(String name) {
        Map<String, TypeDeclaration> cache = getClassCache();
        TypeDeclaration declaration = cache.get(name);

        if (cache.containsKey(name)) {
            return declaration;
        }

        if (declaration == null) {
            declaration = new Resolver(new SourceInfo(0, 0)).tryResolveClass(name);
            cache.put(name, declaration);
        }

        return declaration;
    }

    private static TypeDeclaration get(String name) {
        TypeDeclaration clazz = getOptional(name);
        if (clazz == null) {
            throw new RuntimeException("Class not found: " + name);
        }

        return clazz;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, TypeDeclaration> getClassCache() {
        return (Map<String, TypeDeclaration>) CompilationContext.getCurrent()
            .computeIfAbsent(TypeSymbols.class, key -> new HashMap<String, TypeDeclaration>());
    }
}
