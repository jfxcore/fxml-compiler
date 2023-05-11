plugins {
    `java-library`
    `maven-publish`
    signing
    kotlin("jvm") version "1.5.10"
    id("com.github.johnrengelman.shadow") version "7.1.0"
}

group = "org.jfxcore"
version = project.findProperty("TAG_VERSION_COMPILER") ?: "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven(url = "https://plugins.gradle.org/m2/")
}

sourceSets {
    main {
        java.srcDir("$buildDir/generated/java/main")
    }

    create("compilerTest") {
        java.srcDir("$projectDir/src/compilerTest/java")
        compileClasspath += sourceSets.main.get().compileClasspath + sourceSets.test.get().compileClasspath + sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().runtimeClasspath + sourceSets.test.get().compileClasspath + sourceSets.main.get().output
    }
}

java {
    withSourcesJar()
    withJavadocJar()
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

configurations["compilerTestImplementation"].extendsFrom(configurations.implementation.get())
configurations["compilerTestRuntimeOnly"].extendsFrom(configurations.runtimeOnly.get())

val copyVersionInfo = tasks.create<Copy>("copyVersionInfo") {
    from("$projectDir/src/main/version-info/VersionInfo.java")
    into("$buildDir/generated/java/main/org/jfxcore/compiler")
    filter { it.replace("\${version}", project.version.toString()) }
}

tasks.compileJava {
    dependsOn(copyVersionInfo)
    dependsOn(gradle.includedBuild("jfx").task(":sdk"))
}

tasks.named<Jar>("sourcesJar") {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

tasks.withType<Javadoc> {
    val javadocOptions = options as CoreJavadocOptions
    javadocOptions.addStringOption("Xdoclint:none", "-quiet")
}

tasks.withType<Test> {
    useJUnitPlatform()
    systemProperty("prism.order", "sw")
    systemProperty("glass.platform", "Monocle")
    systemProperty("monocle.platform", "Headless")
    systemProperty("testfx.headless", "true")
    systemProperty("testfx.robot", "glass")
    systemProperty("headless.geometry", "640x480-32")
}

val compilerTest = task<Test>("compilerTest") {
    description = "Runs the compiler tests."
    group = "verification"

    testClassesDirs = sourceSets["compilerTest"].output.classesDirs
    classpath +=
        sourceSets["main"].runtimeClasspath +
        sourceSets["test"].runtimeClasspath +
        sourceSets["compilerTest"].runtimeClasspath

    shouldRunAfter("test")
}

tasks.check { dependsOn(compilerTest) }

tasks.shadowJar {
    archiveClassifier.set("")
    include("*.jar")
    include("META-INF/services/org.jfxcore.*")
    include("org/jfxcore/**/*.*")
    include("kotlinx/**/*.*")
    include("javassist/**/*.*")
    relocate("javassist", "org.jfxcore.javassist")
    relocate("kotlinx", "org.jfxcore.kotlinx")
    dependencies {
        exclude(dependency("org.jetbrains.kotlin:kotlin-stdlib"))
        exclude(dependency("org.jetbrains.kotlin:kotlin-stdlib-common"))
        exclude(dependency("org.jetbrains.kotlin:kotlin-stdlib-jdk7"))
        exclude(dependency("org.jetbrains.kotlin:kotlin-stdlib-jdk8"))
        exclude(dependency("org.jetbrains.kotlin:kotlin-reflect"))
    }
}

tasks.withType<GenerateModuleMetadata> {
    enabled = false
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifact(tasks.shadowJar)
            artifact(tasks["sourcesJar"])
            artifact(tasks["javadocJar"])
            pom {
                url.set("https://github.com/jfxcore/fxml-compiler")
                name.set("fxml-compiler")
                description.set("FXML markup compiler")
                licenses {
                    license {
                        name.set("BSD-3-Clause")
                        url.set("https://opensource.org/licenses/BSD-3-Clause")
                    }
                }
                developers {
                    developer {
                        id.set("jfxcore")
                        name.set("JFXcore")
                        organization.set("JFXcore")
                        organizationUrl.set("https://github.com/jfxcore")
                    }
                }
                scm {
                    connection.set("scm:git:https://github.com/jfxcore/fxml-compiler.git")
                    developerConnection.set("scm:git:https://github.com/jfxcore/fxml-compiler.git")
                    url.set("https://github.com/jfxcore/fxml-compiler")
                }
            }
        }
    }
    repositories {
        maven {
            if (project.hasProperty("REPOSITORY_USERNAME")
                    && project.hasProperty("REPOSITORY_PASSWORD")
                    && project.hasProperty("REPOSITORY_URL")) {
                credentials {
                    username = project.property("REPOSITORY_USERNAME") as String
                    password = project.property("REPOSITORY_PASSWORD") as String
                }
                url = uri(project.property("REPOSITORY_URL") as String)
            }
        }
    }
}

signing {
    sign(publishing.publications["maven"])
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.8.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.testfx:testfx-junit5:4.0.16-alpha")
    testImplementation("org.testfx:openjfx-monocle:jdk-12.0.1+2")
    testImplementation(files("${gradle.includedBuild("jfx").projectDir}/build/sdk/lib/javafx.base.jar"))
    testImplementation(files("${gradle.includedBuild("jfx").projectDir}/build/sdk/lib/javafx.graphics.jar"))
    testImplementation(files("${gradle.includedBuild("jfx").projectDir}/build/sdk/lib/javafx.controls.jar"))
    testImplementation(files("${gradle.includedBuild("jfx").projectDir}/build/sdk/lib/javafx.fxml.jar"))

    implementation("org.javassist:javassist:3.28.0-GA")
    implementation("org.jetbrains.kotlinx:kotlinx-metadata-jvm:0.4.1")

    compileOnly("org.jetbrains:annotations:13.0")
    compileOnly(files("${gradle.includedBuild("jfx").projectDir}/build/sdk/lib/javafx.base.jar"))
    compileOnly(files("${gradle.includedBuild("jfx").projectDir}/build/sdk/lib/javafx.graphics.jar"))
    compileOnly(files("${gradle.includedBuild("jfx").projectDir}/build/sdk/lib/javafx.controls.jar"))
}
