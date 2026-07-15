/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.protobuf.json.grpc

import spock.lang.Specification

class GrpcProxyServiceMemoryExhaustionPocSpec extends Specification {

    def "oversized server stream is rejected before exhausting the proxy heap"() {
        given:
        def javaExecutable = new File(System.getProperty('java.home'), 'bin/java').absolutePath
        def process = new ProcessBuilder(
                javaExecutable,
                '-Xmx48m',
                '-cp', System.getProperty('java.class.path'),
                GrpcProxyServiceMemoryExhaustionHarness.name
        ).redirectErrorStream(true).start()

        when:
        def output = process.inputStream.getText('UTF-8')
        process.waitFor()

        then:
        process.exitValue() != 0
        !output.contains('java.lang.OutOfMemoryError: Java heap space')
        output.contains('gRPC response exceeded the maximum response')
    }
}
