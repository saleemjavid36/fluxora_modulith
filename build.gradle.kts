import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.changelog")
    id("org.jetbrains.intellij.platform")
}

repositories {
    mavenCentral()

    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")

    intellijPlatform {
        intellijIdea("2025.3.5")
        bundledPlugin("com.intellij.java")
        testFramework(TestFrameworkType.Platform)
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "253"
        }
    }
}