package io.micronaut.protobuf.json.transcoding

import io.grpc.Metadata
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor
import io.grpc.Status
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.BlockingHttpClient
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.json.JsonMapper
import io.micronaut.protobuf.transcoding.library.ListBooksRequest
import io.micronaut.protobuf.transcoding.library.UpdateBookRequest
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.inject.Singleton
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class GrpcTranscodingSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
            'spec.name'                    : 'GrpcTranscodingSpec',
            'grpc.rest.transcoding.enabled': true,
    ])

    @Shared
    @AutoCleanup
    HttpClient httpClient = server.applicationContext.createBean(HttpClient, server.URL)

    @Shared
    BlockingHttpClient client = httpClient.toBlocking()

    @Shared
    JsonMapper jsonMapper = server.applicationContext.getBean(JsonMapper)

    @Shared
    LibraryServiceImpl service = server.applicationContext.getBean(LibraryServiceImpl)

    void "GET binds a multi-segment path variable"() {
        when:
        def json = get('/v1/shelves/s1')

        then:
        json == [name: 'shelves/s1', theme: 'Fiction']
    }

    void "GET binds query parameters, repeated parameters and nested fields"() {
        when:
        def json = get('/v1/shelves/s1/books?pageSize=5&tags=a&tags=b&paging.token=t1&unknown=x')

        then:
        ListBooksRequest request = service.lastRequest as ListBooksRequest
        request.parent == 'shelves/s1'
        request.pageSize == 5
        request.tagsList == ['a', 'b']
        request.paging.token == 't1'
        json == [books: [[id: '1', title: 'Dune', tags: ['a', 'b']]], nextPageToken: 't1-next']
    }

    void "path bindings win over query parameters that spell the same field differently"() {
        when:
        def json = get('/v1/pages/5/p1?pageSize=7&page_size=8&paging.pageToken=q&paging.page_token=r&paging.token=t')

        then:
        ListBooksRequest request = service.lastRequest as ListBooksRequest
        request.pageSize == 5
        request.paging.pageToken == 'p1'
        request.paging.token == 't'
        json == [nextPageToken: 'p1-next']
    }

    void "a custom wildcard rule matches any HTTP method"() {
        expect:
        exchange(HttpRequest.GET('/v1/any/1')) == [id: '1', title: 'any']
        service.lastRequest.id == 1
        exchange(HttpRequest.POST('/v1/any/2', '{}')) == [id: '2', title: 'any']
        service.lastRequest.id == 2
        exchange(HttpRequest.PUT('/v1/any/3', '{}')) == [id: '3', title: 'any']
        exchange(HttpRequest.create(HttpMethod.DELETE, '/v1/any/4')) == [id: '4', title: 'any']

        and: 'an exact HTTP method binding wins over the wildcard'
        client.retrieve(HttpRequest.GET('/v1/books/3/title'), String) == '"Title 3"'
        exchange(HttpRequest.POST('/v1/books/3/title', '{}')) == [id: '3', title: 'any title']
    }

    void "POST maps the whole body to the request"() {
        when:
        def json = exchange(HttpRequest.POST('/v1/books', '{"title":"Dune","author":"Herbert","tags":["sf"]}'))

        then:
        json == [id: '42', title: 'Dune', author: 'Herbert', tags: ['sf']]
    }

    void "POST maps the body to a field and query parameters to the other fields"() {
        when:
        def json = exchange(HttpRequest.POST('/v1/shelves?validateOnly=true', '{"name":"shelves/new","theme":"History"}'))

        then:
        service.lastRequest.validateOnly
        json == [name: 'shelves/new', theme: 'History']
    }

    void "PUT and PATCH bind a nested path field and a body field"() {
        when:
        def put = exchange(HttpRequest.PUT('/v1/books/7?reason=typo', '{"title":"Dune Messiah"}'))

        then:
        UpdateBookRequest request = service.lastRequest as UpdateBookRequest
        request.book.id == 7
        request.reason == 'typo'
        put == [id: '7', title: 'Dune Messiah']

        when:
        def patch = exchange(HttpRequest.PATCH('/v1/books/8', '{"author":"Herbert"}'))

        then:
        patch == [id: '8', author: 'Herbert']
    }

    void "DELETE without a body"() {
        when:
        HttpResponse<String> response = client.exchange(HttpRequest.DELETE('/v1/books/9'), String)

        then:
        response.status.code == 200
        response.body() == '{}'
        service.lastRequest.id == 9
    }

    void "response_body returns a single field"() {
        expect:
        client.retrieve(HttpRequest.GET('/v1/books/3/title'), String) == '"Title 3"'
    }

    void "a verb suffix selects the custom method"() {
        expect:
        exchange(HttpRequest.POST('/v1/books/5:archive', '{}')) == [id: '5', archived: true]
    }

    void "gRPC errors map to HTTP status codes"() {
        when:
        client.exchange(HttpRequest.GET('/v1/shelves/missing'), String)

        then:
        HttpClientResponseException notFound = thrown()
        notFound.status.code == 404
        error(notFound) == [code: 404, message: 'No shelf shelves/missing', status: 'NOT_FOUND']

        when:
        client.exchange(HttpRequest.POST('/v1/books', '{"author":"nobody"}'), String)

        then:
        HttpClientResponseException invalid = thrown()
        invalid.status.code == 400
        error(invalid).status == 'INVALID_ARGUMENT'
    }

    void "a body that doesn't fit the request message is a bad request"() {
        when:
        client.exchange(HttpRequest.POST('/v1/books', '{"noSuchField":1}'), String)

        then:
        HttpClientResponseException e = thrown()
        e.status.code == 400
        error(e).status == 'INVALID_ARGUMENT'

        when:
        client.exchange(HttpRequest.POST('/v1/books', 'not json'), String)

        then:
        HttpClientResponseException notJson = thrown()
        notJson.status.code == 400
    }

    void "HTTP headers reach gRPC interceptors as metadata"() {
        when:
        client.exchange(HttpRequest.GET('/v1/shelves/s1').header('Authorization', 'Bearer deny'), String)

        then:
        HttpClientResponseException e = thrown()
        e.status.code == 401
        error(e).status == 'UNAUTHENTICATED'
        server.applicationContext.getBean(DenyingInterceptor).lastAuthorization == 'Bearer deny'
    }

    void "requests without a matching rule reach the application's routes"() {
        expect:
        client.retrieve('/plain') == 'plain'

        when: 'streaming methods are not transcoded'
        client.exchange(HttpRequest.GET('/v1/stream'), String)

        then:
        HttpClientResponseException e = thrown()
        e.status.code == 404

        when: 'a method that has no rule for this HTTP method'
        client.exchange(HttpRequest.create(HttpMethod.DELETE, '/v1/shelves/s1'), String)

        then:
        HttpClientResponseException other = thrown()
        other.status.code in [404, 405]
    }

    void "custom verbs are registered and invalid templates are ignored"() {
        given:
        GrpcTranscodingRegistry registry = server.applicationContext.getBean(GrpcTranscodingRegistry)

        expect:
        registry.find('SEARCH', '/v1/books').method().grpcMethod().fullMethodName == 'library.v1.LibraryService/SearchBooks'
        registry.methods().every { it.grpcMethod().fullMethodName != 'library.v1.LibraryService/BrokenRule' }
        registry.methods().every { it.grpcMethod().fullMethodName != 'library.v1.LibraryService/StreamBooks' }
        registry.find('GET', '/nothing/here') == null
    }

    void "the transcoding beans are absent unless enabled"() {
        given:
        ApplicationContext context = ApplicationContext.run(['spec.name': 'GrpcTranscodingSpec', 'grpc.server.enabled': false])

        expect:
        !context.containsBean(GrpcTranscodingFilter)
        !context.containsBean(GrpcTranscodingRegistry)

        cleanup:
        context.close()
    }

    private Object get(String uri) {
        exchange(HttpRequest.GET(uri))
    }

    private Object exchange(HttpRequest<?> request) {
        jsonMapper.readValue(client.retrieve(request, String), Object)
    }

    private Map error(HttpClientResponseException e) {
        (jsonMapper.readValue(e.response.getBody(String).get(), Map) as Map).error as Map
    }

    @Controller('/plain')
    @Requires(property = 'spec.name', value = 'GrpcTranscodingSpec')
    static class PlainController {
        @Get
        String plain() {
            'plain'
        }
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'GrpcTranscodingSpec')
    static class DenyingInterceptor implements ServerInterceptor {
        static final Metadata.Key<String> AUTHORIZATION = Metadata.Key.of('authorization', Metadata.ASCII_STRING_MARSHALLER)
        volatile String lastAuthorization

        @Override
        <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
            lastAuthorization = headers.get(AUTHORIZATION)
            if (lastAuthorization == 'Bearer deny') {
                call.close(Status.UNAUTHENTICATED.withDescription('denied'), new Metadata())
                return new ServerCall.Listener<ReqT>() {}
            }
            return next.startCall(call, headers)
        }
    }
}
