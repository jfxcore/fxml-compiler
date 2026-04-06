---
layout: default
title: Getting started
nav_order: 2
---

# Getting started
The easiest way to get started with FXML 2.0 is by using the [org.jfxcore.fxmlplugin](https://plugins.gradle.org/plugin/org.jfxcore.fxmlplugin), which automates the process of compiling [standalone](getting-started/standalone.html) or [embedded](getting-started/embedded.html) FXML 2.0 markup code in your Gradle project.
At this point, there is no plugin yet for the Maven build system. Manually invoking the FXML compiler is possible, but not recommended.

## Using the Gradle plugin
Add the following line to the `plugins` block of your `build.gradle` file:
### Kotlin
```kotlin
plugins {
    id("org.jfxcore.fxmlplugin") version "0.12.1"
}
```
### Groovy
```groovy
plugins {
    id "org.jfxcore.fxmlplugin" version "0.12.1"
}
```
After the plugin is applied, `.fxml` files in your Gradle project will be automatically compiled with the rest of your source files.
Annotation processing for [embedded markup](getting-started/embedded.html) is disabled by default and needs to be enabled in the Gradle build script; see [Enable annotation processing](getting-started/embedded.html#enable-annotation-processing).

{: .highlight}
> The plugin adds a new task for each of your source sets to the Gradle project.
> The task is named `processFxml`, `processTestFxml`, etc. and is responsible for parsing FXML markup files and generating Java code files.
>
> Usually you don't need to run these tasks manually, as they are automatically run when you build the project.
