# Python Docs Disabled Test Inventory

This file tracks the Python documentation examples of `test-suite-python` that are disabled, reduced, or carry a
workaround because the direct port of the Java example does not compile or does not behave like the Java example
yet. It is the bug-fixing task list for the Python compiler (`micronaut-inject-python` / `micronaut-context-python`);
every row references a `TODO(python)` comment in the sources.

The Python examples are compiled by every build and their tests run with `./gradlew pythonCheck -Ppython-ci`
(the "Python CI" GitHub workflow).

## Reconciliation

- Last generated active `@Disabled` count: 0.
- Last full-suite command: `./gradlew :test-suite-python:test -Ppython-ci`.
- Last full-suite result: build successful, 11 tests executed, 0 failures (Micronaut core 5.2.3, micronaut-build 8.1.2).

## Migration Rules

- Do not define local copies of Micronaut annotation helpers or custom annotation shims in docs snippets. Standard
  Micronaut annotations are imported from their Java packages (`micronaut.grpc.annotation`, `micronaut.context.annotation`, ...).
- Java classes are imported from their packages (`from micronaut.grpc.annotation import GrpcChannel`,
  `from reactor.core.publisher import Mono`, `from org.example.grpc import GreeterGrpc`); `java.type(...)` is used only
  where the import form fails, see "java.type usages" below.
- Types of the `io.grpc` packages are imported directly (`from io.grpc import ServerInterceptor`).
- Logging uses the Python `logging` module (`LOG = logging.getLogger(__name__)`), not slf4j.
- A gRPC service extends the generated `GreeterGrpc.GreeterImplBase` class like the Java one (the base is loaded with
  `java.type(...)`, see below); the server interceptor extends `io.grpc.ServerInterceptor` and `Ordered`, the builder
  listeners are `BeanCreatedEventListener[ServerBuilder]` / `BeanCreatedEventListener[ManagedChannelBuilder]`.
- Methods that implement a Java interface keep the Java (camelCase) name (`sayHello`, `bindService`, `onCreated`);
  other methods are snake_case.
- Python test classes are `@MicronautTest` classes with injected beans; example beans that must only be active in one
  test are gated with `@Requires(property="spec.name", ...)` outside the snippet tags, the test sets the property with
  `@Property(name="spec.name", ...)`.
- The documentation classes live in `src/main/python` (the `source="main"` snippets), the tests in `src/test/python`.

## java.type usages

Every remaining `java.type(...)` call carries a `# TODO(python): java.type needed because ...` comment.

| Files | Types | Reason |
| --- | --- | --- |
| `GreetingEndpoint.py`, `DiscoveryClients.py`, `DnsClients.py`, `ExternalizedClients.py`, `NamedChannelClients.py`, `GreetingEndpointTest.py`, `DiscoveryClientsTest.py`, `DnsClientsTest.py`, `ExternalizedClientsTest.py`, `ManagedChannelBuilderListenerTest.py`, `NamedChannelClientsTest.py`, `ReactiveGreetingEndpointTest.py`, `ServerBuilderListenerTest.py`, `ServerInterceptorTest.py` | `helloworld.GreeterGrpc`, `helloworld.HelloRequest`, `helloworld.HelloReply`, `helloworld.ReactorReactiveGreeterGrpc` | The generated gRPC classes live in the Java package `helloworld`, which is also the package of the Python documentation sources (the `snippet::helloworld.*` macros). `from helloworld import HelloRequest` compiles with core 5.2.3, but at runtime the package is then served as the Java package: every `helloworld.<Module>` import of a Python module of the same package resolves to the Java class of that name (`ImportError: cannot import name 'Clients' from 'helloworld.GreetingEndpointTest' (unknown location)` while importing the members of the package). The generated classes of the `org.example.grpc` package (`GreeterService.py`) are imported normally. |

## Active `@Disabled` Tests

None.

## Reduced Ports

| Target | Difference |
| --- | --- |
| `helloworld.ReactiveGreetingEndpoint` | Not ported: the generated `ReactorReactiveGreeterGrpc.ReactiveGreeterImplBase` declares two `sayHello` overloads (`Mono<HelloReply> sayHello(HelloRequest)` and `Mono<HelloReply> sayHello(Mono<HelloRequest>)`) and a Python `sayHello(self, request: Mono[HelloRequest])` overrides both (the Python compiler bridges every overload of the name to the Python method), so the unary overload the server calls (`ServerCalls.oneToOne(request, serviceImpl::sayHello, ...)`) hands the raw `HelloRequest` to the Python method and the call fails with `StatusRuntimeException: UNKNOWN`. The snippet is rendered for Java, Kotlin and Groovy only; the Reactor server of this suite is the Java helper `src/main/java/helloworld/ReactiveGreetingEndpoint.java` and the Reactor client stub is used from Python in `ReactiveGreetingEndpointTest.py`. |

## Intentionally Unsupported Snippet Targets

None (the target above is rendered with `languages="java,kotlin,groovy"` and a `[.lang-python]` note).
