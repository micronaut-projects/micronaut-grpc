package io.micronaut.grpc.channels

import spock.lang.Specification
import spock.lang.Unroll

class GrpcManagedChannelConfigurationSpec extends Specification {

    @Unroll
    void "formatTarget('#host', #port) == '#expected'"() {
        expect:
        GrpcManagedChannelConfiguration.formatTarget(host, port) == expected

        where:
        host         | port | expected
        'myservice'  | 8443 | 'myservice:8443'
        '::1'        | 8443 | '[::1]:8443'
        '[::1]'      | 8443 | '[::1]:8443'
    }
}
