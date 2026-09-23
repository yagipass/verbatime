plugins {
    java
}

group = "io.github.yagipass"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

sourceSets {
    main {
        java.srcDir("../workload/src/main/java")
    }
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    maxParallelForks = 1
    forkEvery = 0
    if (file("/work/verbatime-agent.jar").exists()) {
        jvmArgs(
            "-javaagent:/work/verbatime-agent.jar=record=startup," +
                "roots=org.junit.jupiter.engine.descriptor.TestMethodTestDescriptor::execute," +
                "exclude=org.gradle+worker.org.gradle," +
                "out=/work/recordings/junit-gradle.vbtm",
        )
    }
    testLogging {
        events("passed", "failed")
        showStandardStreams = true
    }
}
