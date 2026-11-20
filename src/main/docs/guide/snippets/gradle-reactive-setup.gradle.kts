// tag::variables[]
val reactiveGrpcVersion = "{reactive-grpc-version}"
// end::variables[]

dependencies {
    // tag::dependencies[]
    implementation("com.salesforce.servicelibs:reactor-grpc-stub:$reactiveGrpcVersion")
    implementation("io.projectreactor:reactor-core")
    // end::dependencies[]
}

protobuf {
    plugins {
        // tag::plugin[]
        create("reactor") { artifact = "com.salesforce.servicelibs:reactor-grpc:$reactiveGrpcVersion" }
        // end::plugin[]
    }
    generateProtoTasks {
        all().configureEach {
            plugins {
                // tag::config[]
                create("reactor")
                // end::config[]
            }
        }
    }
}
