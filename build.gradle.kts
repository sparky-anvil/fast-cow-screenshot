import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.multiplatform)
}

group = "by.sparkyanvil.fastcowscreenshot"
version = "1.0.0"

kotlin {
    jvmToolchain(25)
}

dependencies {
    implementation(libs.compose.desktop.windows)
    implementation(libs.compose.material)
    implementation(libs.jna)
    implementation(libs.jna.platform)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.slf4j.api)
    implementation(libs.logback.classic)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.mockk)
    testRuntimeOnly(libs.junit.platform.launcher)
}

val appJvmArgs = listOf(
    "--enable-native-access=ALL-UNNAMED",
    "--sun-misc-unsafe-memory-access=allow",
    "-Xmx768m",
    // Возврат свободной памяти heap ОС в простое (REQ-001.N6, CLR-005).
    "-XX:G1PeriodicGCInterval=15000",
    "-XX:MinHeapFreeRatio=10",
    "-XX:MaxHeapFreeRatio=20",
)

tasks.test {
    useJUnitPlatform()
    jvmArgs(appJvmArgs)
    // MockK: агент ByteBuddy подключается при старте JVM (-javaagent), а не self-attach,
    // который на JDK 25 не работает и ломается на путях с кириллицей (CLR-001, CLR-002).
    doFirst {
        val agentJar = classpath.first { it.name.startsWith("byte-buddy-agent") }
        jvmArgs("-javaagent:${agentJar.absolutePath}", "-Xshare:off")
    }
}

compose.desktop {
    application {
        mainClass = "by.sparkyanvil.fastcowscreenshot.MainKt"
        jvmArgs += appJvmArgs
        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "fast-cow-screenshot"
            packageVersion = "1.0.0"
            // Temurin 25 без jmods (JEP 493): jdk.jlink в образ включать нельзя, поэтому список модулей явный (CLR-004).
            modules(
                "java.datatransfer",
                "java.desktop",
                "java.instrument",
                "java.logging",
                "java.management",
                "java.naming",
                "java.prefs",
                "java.sql",
                "java.xml",
                "jdk.accessibility",
                "jdk.charsets",
                "jdk.localedata",
                "jdk.management",
                "jdk.unsupported",
                "jdk.unsupported.desktop",
                "jdk.zipfs",
            )
        }
    }
}