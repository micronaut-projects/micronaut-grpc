package io.micronaut.protobuf.json.transcoding

import spock.lang.Specification

class HttpRuleTemplateSpec extends Specification {

    void "templates match paths and bind variables"() {
        expect:
        HttpRuleTemplate.parse(template).match(path) == expected

        where:
        template                                  | path                               | expected
        '/v1/shelves'                             | '/v1/shelves'                      | [:]
        '/v1/shelves/{shelf}'                     | '/v1/shelves/s1'                   | [shelf: 's1']
        '/v1/{name=shelves/*}'                    | '/v1/shelves/s1'                   | [name: 'shelves/s1']
        '/v1/{name=shelves/*}/books/{book}'       | '/v1/shelves/s1/books/b2'          | [name: 'shelves/s1', book: 'b2']
        '/v1/{name=files/**}'                     | '/v1/files/a/b/c.txt'              | [name: 'files/a/b/c.txt']
        '/v1/{book.id}'                           | '/v1/12'                           | ['book.id': '12']
        '/v1/books/{id}:archive'                  | '/v1/books/5:archive'              | [id: '5']
        '/v1/*/books'                             | '/v1/anything/books'               | [:]
        '/v1/shelves/{shelf}'                     | '/v1/shelves/a%20b'                | [shelf: 'a b']
        '/v1/shelves/{shelf}'                     | '/v1/shelves/a+b%zz'               | [shelf: 'a+b%zz']
        '/v1/shelves/{shelf}'                     | '/v1/shelves/s1/extra'             | null
        '/v1/books/{id}:archive'                  | '/v1/books/5'                      | null
        '/v1/shelves'                             | '/v2/shelves'                      | null
    }

    void "literal length ranks more specific templates first"() {
        expect:
        HttpRuleTemplate.parse('/v1/shelves/special').literalLength() > HttpRuleTemplate.parse('/v1/shelves/{shelf}').literalLength()
        HttpRuleTemplate.parse('/v1/{name=shelves/*}').variables() == ['name']
        HttpRuleTemplate.parse('/v1/x').template() == '/v1/x'
    }

    void "invalid templates are rejected"() {
        when:
        HttpRuleTemplate.parse(template)

        then:
        thrown(IllegalArgumentException)

        where:
        template << ['v1/shelves', '/v1//shelves', '/v1/{shelf', '/v1/**/books', '/v1/{}', '/v1/{a={b}}', '/v1/x:', '/v1/a{b}']
    }
}
