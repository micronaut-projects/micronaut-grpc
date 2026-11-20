plugins {
    ...
    // tag::plugin[]
    id("com.google.protobuf") version "{protobuf-gradle-plugin-version}"
    // end::plugin[]
}

// tag::config[]
val grpcVersion = "{grpc-version}"
val protobufVersion = "{protobuf-version}"

protobuf {
    protoc { artifact = "com.google.protobuf:protoc:$protobufVersion" }
    plugins {
        create("grpc") { artifact = "io.grpc:protoc-gen-grpc-java:$grpcVersion" }
    }
    generateProtoTasks {
        all().configureEach {
            plugins {
                create("grpc")
            }
        }
    }
}
// end::config[]

// tag::omit-generated[]
protobuf {
    generateProtoTasks {
        all().configureEach {
            plugins {
                named("grpc") {
                    option("@generated=omit")
                }
            }
        }
    }
}
// end::omit-generated[]
