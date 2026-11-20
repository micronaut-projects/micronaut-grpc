package io.micronaut.protobuf.json.transcoding

import com.google.protobuf.DynamicMessage
import com.google.protobuf.InvalidProtocolBufferException
import io.micronaut.protobuf.transcoding.library.Book
import io.micronaut.protobuf.transcoding.library.CreateShelfRequest
import io.micronaut.protobuf.transcoding.library.LibraryServiceGrpc
import io.micronaut.protobuf.transcoding.library.ListBooksRequest
import spock.lang.Specification

class HttpRuleMessageMapperSpec extends Specification {

    HttpRuleMessageMapper mapper = new HttpRuleMessageMapper()

    void "field paths resolve by proto and JSON names"() {
        expect:
        HttpRuleMessageMapper.resolve(ListBooksRequest.descriptor, 'page_size').name == 'page_size'
        HttpRuleMessageMapper.resolve(ListBooksRequest.descriptor, 'pageSize').name == 'page_size'
        HttpRuleMessageMapper.resolve(ListBooksRequest.descriptor, 'paging.token').name == 'token'
        HttpRuleMessageMapper.resolve(ListBooksRequest.descriptor, 'paging.missing') == null
        HttpRuleMessageMapper.resolve(ListBooksRequest.descriptor, 'page_size.nested') == null
        HttpRuleMessageMapper.resolve(ListBooksRequest.descriptor, 'tags.nested') == null
    }

    void "a whole-body binding requires a JSON object"() {
        when:
        mapper.request(method('CreateBook', '*', ''), [:], [:], '["not","an","object"]')

        then:
        thrown(InvalidProtocolBufferException)
    }

    void "a path variable must name a request field"() {
        when:
        mapper.request(method('CreateBook', '', ''), [nope: '1'], [:], null)

        then:
        InvalidProtocolBufferException e = thrown()
        e.message.contains("Path variable 'nope'")
    }

    void "query parameters bound by the body field are ignored"() {
        when:
        DynamicMessage request = mapper.request(method('CreateShelf', 'shelf', ''), [:], ['shelf.name': ['ignored'], validate_only: ['true']], '{"name":"n"}')

        then:
        CreateShelfRequest.parseFrom(request.toByteArray()).shelf.name == 'n'
        CreateShelfRequest.parseFrom(request.toByteArray()).validateOnly
    }

    void "bound fields are compared by their canonical field path"() {
        when:
        DynamicMessage request = mapper.request(method('ListPage', '', ''),
                [page_size: '5', 'paging.page_token': 'p'],
                [pageSize: ['7'], 'paging.pageToken': ['q'], paging: ['ignored'], tags: ['a']],
                null)
        ListBooksRequest parsed = ListBooksRequest.parseFrom(request.toByteArray())

        then:
        parsed.pageSize == 5
        parsed.paging.pageToken == 'p'
        parsed.tagsList == ['a']
    }

    void "query parameters spelling the same field differently set it once"() {
        when:
        DynamicMessage request = mapper.request(method('ListPage', '', ''), [:], [pageSize: ['7'], page_size: ['8'], 'paging.pageToken': ['q'], 'paging.page_token': ['r']], null)
        ListBooksRequest parsed = ListBooksRequest.parseFrom(request.toByteArray())

        then:
        parsed.pageSize in [7, 8]
        parsed.paging.pageToken in ['q', 'r']
    }

    void "a path variable spelled with its JSON name sets the field once"() {
        when:
        DynamicMessage request = mapper.request(method('ListPage', '', ''), [pageSize: '5'], [page_size: ['7']], null)

        then:
        ListBooksRequest.parseFrom(request.toByteArray()).pageSize == 5
    }

    void "a path variable replaces a whole-body field given by its JSON name"() {
        when:
        DynamicMessage request = mapper.request(method('ListPage', '*', ''), [page_size: '5', 'paging.page_token': 'p'],
                [:], '{"pageSize":7,"paging":{"pageToken":"q","token":"t"}}')
        ListBooksRequest parsed = ListBooksRequest.parseFrom(request.toByteArray())

        then:
        parsed.pageSize == 5
        parsed.paging.pageToken == 'p'
        parsed.paging.token == 't'
    }

    void "an empty body leaves the body field unset"() {
        expect:
        mapper.request(method('CreateShelf', 'shelf', ''), [:], [:], '  ').allFields.isEmpty()
    }

    void "response_body renders default values and rejects unknown fields"() {
        given:
        DynamicMessage response = DynamicMessage.parseFrom(Book.descriptor, Book.getDefaultInstance().toByteArray())

        expect:
        mapper.response(method('CreateBook', '*', 'title'), response) == '""'
        mapper.response(method('CreateBook', '*', 'tags'), response) == '[]'

        when:
        mapper.response(method('CreateBook', '*', 'nope'), response)

        then:
        thrown(InvalidProtocolBufferException)
    }

    private static TranscodedMethod method(String name, String body, String responseBody) {
        def proto = LibraryServiceGrpc.serviceDescriptor.schemaDescriptor.serviceDescriptor.findMethodByName(name)
        new TranscodedMethod('POST', HttpRuleTemplate.parse('/x'), body, responseBody, null, proto)
    }
}
