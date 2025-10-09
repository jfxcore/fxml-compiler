# Overview
FXML 2.0 is a markup language that makes it easy to declaratively build JavaFX applications.
In contrast to classic FXML files, which are parsed and loaded at runtime with `FXMLLoader`, FXML 2.0 files
are compiled directly to bytecode.

Since the compiled files are just regular class files, no repeated parsing and loading is required at runtime.
In many cases, scene graph loading performance can be significantly improved compared to `FXMLLoader`.

Additionally, FXML 2.0 offers many new features and a more concise syntax that results in significantly
shorter markup code. Refer to the [FXML 2.0 documentation](https://jfxcore.github.io/fxml-compiler/) to get started.

## Getting started

##### Using the <a href="https://docs.gradle.org/current/userguide/plugins.html#sec:plugins_block">plugins DSL</a>:

**Groovy**
```groovy
plugins {
    id "org.jfxcore.fxmlplugin" version "0.12.1"
}
```

**Kotlin**
```kotlin
plugins {
    id("org.jfxcore.fxmlplugin") version "0.12.1"
}
```

##### Using <a href="https://docs.gradle.org/current/userguide/plugins.html#sec:old_plugin_application">legacy plugin application</a>:

**Groovy**
```groovy
buildscript {
  repositories {
    maven {
      url "https://plugins.gradle.org/m2/"
    }
  }
  dependencies {
    classpath "org.jfxcore:fxml-gradle-plugin:0.12.1"
  }
}

apply plugin: "org.jfxcore.fxmlplugin"
```

**Kotlin**
```kotlin
buildscript {
  repositories {
    maven {
      url = uri("https://plugins.gradle.org/m2/")
    }
  }
  dependencies {
    classpath("org.jfxcore:fxml-gradle-plugin:0.12.1")
  }
}

apply(plugin = "org.jfxcore.fxmlplugin")
```
