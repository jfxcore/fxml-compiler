module org.jfxcore.compiler {
    requires java.base;

    requires static java.compiler;
    requires static jdk.compiler;
    requires static jdk.xml.dom;
    requires static javafx.base;
    requires static javafx.graphics;
    requires static org.javassist;
    requires static org.jetbrains.annotations;
    requires static symbol.processing.api;
    requires static kotlin.stdlib;

    exports org.jfxcore.markup.embed;
}
