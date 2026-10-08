plugins {
    id("io.micronaut.build.internal.grpc-base")
    id("io.micronaut.build.internal.java-base")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

dependencies {
    testImplementation(platform(mn.micronaut.core.bom))
    testImplementation(projects.micronautGrpcServerRuntime)
    testImplementation(projects.micronautGrpcClientRuntime)
    testImplementation(mn.micronaut.dev.tck)
    // the reload harness compiles the application under test with the processors on the test classpath
    testImplementation(mn.micronaut.inject.java)
    testImplementation(libs.grpc.netty)
    testImplementation(mnTest.junit.jupiter.api)
    testRuntimeOnly(mnTest.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testRuntimeOnly(mnLogging.logback.classic)
}
