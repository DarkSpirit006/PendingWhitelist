plugins {
    java
    checkstyle
}

// Keep the release version here; paper-plugin.yml receives it during processResources.
group = "dev.darkspirit69"
version = "2.2.0"
description = "Tracks players rejected by a server whitelist and provides a graphical admin interface."
val pluginVersion = version.toString()

repositories {
    mavenCentral()
    maven {
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.20.1-R0.1-SNAPSHOT")
    implementation("com.google.code.gson:gson:2.13.0")
}

java {
    // Build with JDK 25 while emitting Java 21-compatible bytecode for Paper 1.20.1+.
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

checkstyle {
    configFile = file("checkstyle.xml")
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
    options.encoding = "UTF-8"
}

// Expand the generated plugin metadata so there is only one version to maintain.
tasks.processResources {
    filesMatching("paper-plugin.yml") {
        expand(mapOf("version" to pluginVersion))
    }
}

// Keep the release JAR versioned so artifacts are unambiguous.
tasks.jar {
    archiveBaseName.set("PendingWhitelist")
    archiveVersion.set(pluginVersion)
    manifest {
        attributes(
            mapOf(
                "Implementation-Title" to project.name,
                "Implementation-Version" to pluginVersion,
                "Implementation-Vendor" to "Dark_Spirit69"
            )
        )
    }
}

