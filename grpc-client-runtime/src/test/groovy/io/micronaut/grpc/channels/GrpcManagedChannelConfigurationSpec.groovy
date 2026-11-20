package io.micronaut.grpc.channels

import spock.lang.Specification

class GrpcManagedChannelConfigurationSpec extends Specification {

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
