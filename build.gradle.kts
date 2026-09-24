plugins {
    java
    checkstyle
    id("com.gradleup.shadow") version "9.6.1"
}

// paper-plugin.yml receives the version during processResources.
group = "dev.darkspirit69"
version = "2.2.2"
description = "Tracks players rejected by a server whitelist and provides a graphical admin interface."
val pluginVersion = version.toString()

repositories {
    mavenCentral()
    maven {
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.126-stable")
    implementation("com.google.code.gson:gson:2.13.0")
    implementation("org.bstats:bstats-bukkit:3.2.1")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    // Build with the JDK required by the newest supported Paper runtime.
    // The emitted bytecode remains Java 17-compatible for the full 1.20-26.3 range.
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

checkstyle {
    configFile = file("checkstyle.xml")
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
}

// Keep the version in one place.
tasks.processResources {
    filesMatching("paper-plugin.yml") {
        expand(mapOf("version" to pluginVersion))
    }
}

// The Shadow JAR is the release artifact.
tasks.jar {
    enabled = false
}

// Bundle runtime dependencies into the release JAR.
tasks.shadowJar {
    archiveBaseName.set("PendingWhitelist")
    archiveVersion.set(pluginVersion)
    archiveClassifier.set("")
    configurations = listOf(project.configurations.runtimeClasspath.get())

    // PendingWhitelist uses Gson directly; keep it in the release JAR.
    relocate("org.bstats", "dev.darkspirit69.pendingwhitelist.libs.bstats")
    relocate("com.google.gson", "dev.darkspirit69.pendingwhitelist.libs.gson")

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

// The release artifact is the shaded JAR.
tasks.build {
    dependsOn(tasks.shadowJar)
}

