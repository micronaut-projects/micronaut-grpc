package io.micronaut.protobuf.json.transcoding

import io.grpc.Status
import spock.lang.Specification

class GrpcHttpStatusSpec extends Specification {

    void "gRPC status codes map to HTTP status codes"() {
        expect:
        GrpcHttpStatus.of(code) == http

        where:
        code                            | http
        Status.Code.OK                  | 200
        Status.Code.CANCELLED           | 499
        Status.Code.UNKNOWN             | 500
        Status.Code.INVALID_ARGUMENT    | 400
        Status.Code.DEADLINE_EXCEEDED   | 504
        Status.Code.NOT_FOUND           | 404
        Status.Code.ALREADY_EXISTS      | 409
        Status.Code.PERMISSION_DENIED   | 403
        Status.Code.RESOURCE_EXHAUSTED  | 429
        Status.Code.FAILED_PRECONDITION | 400
        Status.Code.ABORTED             | 409
        Status.Code.OUT_OF_RANGE        | 400
        Status.Code.UNIMPLEMENTED       | 501
        Status.Code.INTERNAL            | 500
        Status.Code.UNAVAILABLE         | 503
        Status.Code.DATA_LOSS           | 500
        Status.Code.UNAUTHENTICATED     | 401
    }
}
