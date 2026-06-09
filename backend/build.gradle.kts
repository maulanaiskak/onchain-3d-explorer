plugins {
    java
    id("org.springframework.boot") version "3.5.0"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.maul"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
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
    // WebFlux reactive web
    implementation("org.springframework.boot:spring-boot-starter-webflux")

    // Reactive DB (R2DBC)
    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
    implementation("org.postgresql:r2dbc-postgresql:1.0.5.RELEASE")

    // JDBC (Flyway migrations + pgvector queries on bounded elastic)
    implementation("org.postgresql:postgresql:42.7.3")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")

    // Flyway
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    // Actuator
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Caffeine cache (in-memory dedup)
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")

    // Google Auth (Vertex AI ADC)
    implementation("com.google.auth:google-auth-library-oauth2-http:1.23.0")

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
}

dependencyManagement {
    imports {
        mavenBom("org.testcontainers:testcontainers-bom:1.20.1")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
