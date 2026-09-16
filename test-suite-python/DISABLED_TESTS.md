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
- Last full-suite result: build successful, 11 tests executed, 0 failures.

## Migration Rules

- Do not define local copies of Micronaut annotation helpers or custom annotation shims in docs snippets. Standard
  Micronaut annotations are imported from their Java packages (`micronaut.grpc.annotation`, `micronaut.context.annotation`, ...).
- Java classes are imported from their packages (`from micronaut.grpc.annotation import GrpcChannel`,
  `from reactor.core.publisher import Mono`, `from org.example.grpc import GreeterGrpc`); `java.type(...)` is used only
  where the import form fails, see "java.type usages" below.
- Types of the `io.grpc` packages are imported inside a `try:` block with an `except ImportError` fallback to the
  generated `grpc.*` shim packages (`io` is the Python standard library module, so `from io.grpc import ...` fails at
  runtime until the compiler fix for `io.*` packages is released).
- Logging uses the Python `logging` module (`LOG = logging.getLogger(__name__)`), not slf4j.
- A Python class cannot extend the generated `GreeterImplBase` classes: a gRPC service implements the
  `io.grpc.BindableService` interface (imported through the shim, a `java.type(...)` interface as a base class breaks
  constructor injection with `ArityException: Arity error - expected: 1 actual: 2`), implements the methods of the
  generated `AsyncService` interface and returns `GreeterGrpc.bindService(self)` from `bindService`.
- Methods that implement a Java interface keep the Java (camelCase) name (`sayHello`, `bindService`, `onCreated`);
  other methods are snake_case.
- Python test classes are `@MicronautTest` classes with injected beans; example beans that must only be active in one
  test are gated with `@Requires(property="spec.name", ...)` outside the snippet tags, the test sets the property with
  `@Property(name="spec.name", ...)`.
- The documentation classes live in `src/main/python` (the `source="main"` snippets) and the tests in `src/test/python`;
  both roots are merged and compiled together by `compileTestPython` (see `build.gradle`).

## java.type usages

Every remaining `java.type(...)` call carries a `# TODO(python): java.type needed because ...` comment.

| Files | Types | Reason |
| --- | --- | --- |
| `GreetingEndpoint.py`, `DiscoveryClients.py`, `DnsClients.py`, `ExternalizedClients.py`, `NamedChannelClients.py`, `GreetingEndpointTest.py`, `DiscoveryClientsTest.py`, `DnsClientsTest.py`, `ExternalizedClientsTest.py`, `ManagedChannelBuilderListenerTest.py`, `NamedChannelClientsTest.py`, `ReactiveGreetingEndpointTest.py`, `ServerBuilderListenerTest.py`, `ServerInterceptorTest.py` | `helloworld.GreeterGrpc`, `helloworld.HelloRequest`, `helloworld.HelloReply`, `helloworld.ReactorReactiveGreeterGrpc` | The generated gRPC classes live in the Java package `helloworld`, which is also the package of the Python documentation sources (the `snippet::helloworld.*` macros). `from helloworld import HelloRequest` fails to compile: `Failed to write Python code to [GRAALPY-VFS/micronaut-application/src/helloworld/__init__.py]: Output stream or writer has already been opened` (the Java shim package collides with the Python package of the same name). The generated classes of the `org.example.grpc` package (`GreeterService.py`) are imported normally. |

## Active `@Disabled` Tests

None.

## Reduced Ports

| Target | Difference |
| --- | --- |
| `helloworld.ReactiveGreetingEndpoint` | Not ported: a Python class cannot extend the generated Reactor base class `ReactorReactiveGreeterGrpc.ReactiveGreeterImplBase` (and reactive-grpc generates no interface to implement). The snippet is rendered for Java, Kotlin and Groovy only; the Reactor server of this suite is the Java helper `src/main/java/helloworld/ReactiveGreetingEndpoint.java` and the Reactor client stub is used from Python in `ReactiveGreetingEndpointTest.py`. |
| `helloworld.CustomInterceptor` | A Python class cannot implement `io.grpc.ServerInterceptor`: the stub generated for the generic method `<ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT>, Metadata, ServerCallHandler<ReqT, RespT>)` erases the type variables of the `ServerCall` parameter and return type to `Object` (`ServerCall.Listener<Object> interceptCall(ServerCall<Object, Object> call, Metadata headers, ServerCallHandler<ReqT, RespT> next)`), so javac fails with "is not abstract and does not override abstract method". Declaring `TypeVar`s in the Python signature does not help. The Python `CustomInterceptor` is a plain object (no `ServerInterceptor`/`Ordered` bases) registered through the `OrderedServerInterceptor` of `ServerInterceptorFactory` (GraalPy host interop implements the interface); the `Ordered` snippet is rendered for Java, Kotlin and Groovy only and `ServerInterceptorTest.py` expects a single interception. |
| `helloworld.ServerBuilderListener` | `BeanCreatedEventListener[ServerBuilder]` fails to compile (`PythonStubGenerator` failed during `visitClass`: `StackOverflowError`, the self-referencing generic `ServerBuilder<T extends ServerBuilder<T>>`); `BeanCreatedEventListener[NettyServerBuilder]` compiles but silently emits `BeanCreatedEventListener<Object>` (the listener would be invoked for every bean and, being an `Object` listener, is instantiated before the GraalPy context exists: `GraalPy context has not been initialized`). The Python listener listens for the `GrpcServerConfiguration` bean instead and customizes its `getServerBuilder()`, from which the `ServerBuilder` bean is created. |
| `helloworld.ManagedChannelBuilderListener` | Same limitation for `ManagedChannelBuilder<T extends ManagedChannelBuilder<T>>` / `NettyChannelBuilder`. The Python listener listens for the `GrpcManagedChannelConfiguration` beans of the named channels (`grpc.channels.[NAME]`) and customizes their `getChannelBuilder()`; unlike the Java listener it does not apply to the default (unnamed) channels, whose configuration is not a bean. |

## Intentionally Unsupported Snippet Targets

None (the two targets above are rendered with `languages="java,kotlin,groovy"` and a `[.lang-python]` note).
