plugins {
    ...
    // tag::plugin[]
    id("com.google.protobuf") version "{protobuf-gradle-plugin-version}"
    // end::plugin[]
}

// tag::variables[]
val grpcVersion = "{grpc-version}"
val grpcKotlinVersion = "{grpc-kotlin-version}"
val protobufVersion = "{protobuf-version}"
// end::variables[]

dependencies {
    // tag::dependencies[]
    implementation("io.grpc:grpc-kotlin-stub:$grpcKotlinVersion")
    implementation("io.grpc:grpc-services:$grpcVersion")
    compileOnly("io.grpc:grpc-stub:$grpcVersion")
    compileOnly("com.google.protobuf:protobuf-java:$protobufVersion")
    compileOnly("javax.annotation:javax.annotation-api:{javax-annotation-api-version}")
    // end::dependencies[]
}

// tag::config[]
protobuf {
    protoc { artifact = "com.google.protobuf:protoc:$protobufVersion" }
    plugins {
        create("grpc") { artifact = "io.grpc:protoc-gen-grpc-java:$grpcVersion" }
        create("grpckt") { artifact = "io.grpc:protoc-gen-grpc-kotlin:$grpcKotlinVersion:jdk8@jar" }
    }
    generateProtoTasks {
        all().configureEach {
            plugins {
                create("grpc")
                create("grpckt")
            }
        }
    }
}
// end::config[]
