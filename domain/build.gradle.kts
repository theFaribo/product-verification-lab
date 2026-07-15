plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(21)

    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

dependencies {
    testImplementation(kotlin("test"))
}