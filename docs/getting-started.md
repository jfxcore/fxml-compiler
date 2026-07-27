---
layout: default
title: Getting started
nav_order: 2
---

# Getting started
The easiest way to get started with FXML/2 is by using the [org.jfxcore.fxmlplugin](https://plugins.gradle.org/plugin/org.jfxcore.fxmlplugin),
which automates the process of compiling [standalone](getting-started/standalone.html) or [embedded](getting-started/embedded.html) FXML markup code in your Gradle project.
At this point, there is no plugin yet for the Maven build system. Manually invoking the FXML compiler is possible, but not recommended.

## Using the Gradle plugin
Add the following line to the `plugins` block of your `build.gradle` file:
<div class="filename">Kotlin</div>
```kotlin
plugins {
    id("org.jfxcore.fxmlplugin") version "0.15.1"
}
```
<div class="filename">Groovy</div>
```groovy
plugins {
    id "org.jfxcore.fxmlplugin" version "0.15.1"
}
```
After the plugin is applied, `.fxml` files in your Gradle project will be automatically compiled with the rest of your source files.
Annotation processing for [embedded markup](getting-started/embedded.html) is disabled by default and needs to be enabled in the Gradle build script;
see [Enable annotation processing](getting-started/embedded.html#enable-annotation-processing).

{: .highlight}
> The plugin adds a new task for each of your source sets to the Gradle project.
> The task is named `processFxml`, `processTestFxml`, etc. and is responsible for parsing FXML markup files and generating Java code files.
>
> Usually you don't need to run these tasks manually, as they are automatically run when you build the project.

### Configuration
The plugin registers an extension named `fxml` with the following configuration options:

| Option | Default | Description |
| --- | --- | --- |
| `annotationProcessing` | `false` | Specifies whether the plugin processes the `@ComponentView` annotation; see [Enable annotation processing](getting-started/embedded.html#enable-annotation-processing). |
| `sourceFileExtensions` | `["fxml"]` | Specifies the file extensions used to select FXML source files for compilation. |

### Gradual migration of legacy FXML to FXML/2
Specifying a custom file extension can be used to gradually migrate a project containing legacy FXML files to FXML/2.
For example, the following configuration selects `.fxmlx` files for compilation and leaves `.fxml` files unprocessed
by the FXML/2 compiler:

```kotlin
fxml {
    sourceFileExtensions = listOf("fxmlx")
}
```

{: .note}
It is advisable to use the `fxmlx` extension in migration scenarios, as it is also recognized by the FXML/2 IntelliJ IDEA Plugin.

## Using the IntelliJ IDEA plugin
The [FXML/2 IntelliJ IDEA plugin](https://plugins.jetbrains.com/plugin/32337-fxml-2-for-javafx) enables IDE support for
FXML/2 markup files in IntelliJ IDEA, which significantly improves the developer experience. Features of the plugin include:

* Syntax highlighting, folding, formatting, and EditorConfig-aware indentation
* Tag and attribute resolution, code completion, and navigation to JavaFX classes
* Rename, find usages, and go to declaration for `fx:id` and bindings
* Inspections for unresolved tags and attributes, unused imports, invalid values, and more
* Import optimization and intentions to move markup between `.fxml` files and [embedded markup](getting-started/embedded.html)

The plugin is available on the JetBrains Marketplace.
