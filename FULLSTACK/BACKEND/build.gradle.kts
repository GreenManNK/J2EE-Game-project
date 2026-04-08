plugins {
    java
    id("org.springframework.boot") version "3.3.8"
    id("io.spring.dependency-management") version "1.1.6"
}

group = "com.caro"
version = "1.0.0"
description = "Java conversion of the original ASP.NET Caro project"

val gameJavaVersion = providers.gradleProperty("gameJavaVersion")
    .orElse(providers.environmentVariable("GAME_JAVA_VERSION"))
    .orElse("17")
val gameJavaRelease = gameJavaVersion.map { it.toInt() }
val gameJavaLanguageVersion = gameJavaRelease.map { JavaLanguageVersion.of(it) }

java {
    sourceCompatibility = JavaVersion.toVersion(gameJavaVersion.get())
    targetCompatibility = JavaVersion.toVersion(gameJavaVersion.get())
    toolchain {
        languageVersion.set(gameJavaLanguageVersion)
    }
}

sourceSets {
    named("main") {
        java.setSrcDirs(listOf("services/game-platform-service/src/main/java"))
        resources.setSrcDirs(
            listOf(
                "services/game-platform-service/src/main/resources",
                "../FRONTEND/apps/user-web/src/main/resources",
            )
        )
    }
    named("test") {
        java.setSrcDirs(listOf("services/game-platform-service/src/test/java"))
        resources.setSrcDirs(listOf("services/game-platform-service/src/test/resources"))
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("org.apache.poi:poi-ooxml:5.2.5")

    runtimeOnly("com.mysql:mysql-connector-j")
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("com.h2database:h2")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(gameJavaRelease)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    val profile = providers.gradleProperty("springProfile")
        .orElse(providers.environmentVariable("SPRING_PROFILES_ACTIVE"))
        .orElse("prod")
    val serverPort = providers.gradleProperty("serverPort")
        .orElse(providers.environmentVariable("SERVER_PORT"))
        .orElse("8080")

    systemProperty("spring.profiles.active", profile.get())
    args("--server.port=${serverPort.get()}")
}
