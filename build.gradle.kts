import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.changelog")
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.kotlin.plugin.serialization")
}

group   = "com.bowiesolutions"
version = "1.0.0"

kotlin {
    jvmToolchain(21)
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2025.1")
        testFramework(TestFrameworkType.Platform)
        bundledPlugin("Git4Idea")
    }

    // kotlinx-serialization: compileOnly so the platform's bundled copy is
    // used at runtime (avoids classloader conflicts); explicit for tests since
    // the platform classloader is absent there.
    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // kotlinx-coroutines: compileOnly for the same reason.
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("io.mockk:mockk:1.13.12")
}

intellijPlatform {
    pluginConfiguration {
        name    = "weAudit"
        version = project.version.toString()

        ideaVersion {
            sinceBuild = "251"          // IntelliJ 2025.1
            untilBuild = provider { null }
        }

        changeNotes.set(
            provider {
                changelog.renderItem(
                    changelog.getOrNull(project.version.toString())
                        ?: changelog.getLatest(),
                    org.jetbrains.changelog.Changelog.OutputType.HTML
                )
            }
        )
    }

    signing {
        certificateChainFile = file("secrets/chain.crt").takeIf { it.exists() }
            ?: file("secrets/chain.crt")
        privateKeyFile = file("secrets/private.pem").takeIf { it.exists() }
            ?: file("secrets/private.pem")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }
}

changelog {
    version.set(project.version.toString())
    header.set(provider { "[${version.get()}]" })
    groups.set(listOf("Added", "Changed", "Fixed", "Removed"))
}

tasks {
    test {
        useJUnitPlatform()
    }

    buildSearchableOptions {
        enabled = false
    }
    
    prepareJarSearchableOptions {
        enabled = false
    }
}
