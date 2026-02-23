plugins {
    alias {
        libs.plugins.kotlin.jvm
    }
}

group = "org.example"
version = "1.0-SNAPSHOT"

dependencies {
    implementation(libs.bundles.logging)
    implementation(libs.bundles.telegramBot)
    implementation(libs.kotlinx.coroutines)
    implementation(libs.dotenv)

    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(25)
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar.configure {
    manifest {
        attributes(mapOf("Main-Class" to "tgFileBot.MainKt"))
    }
    configurations["compileClasspath"].forEach { file: File ->
        from(zipTree(file.absoluteFile))
    }
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}