# Example and test for micronaut-protobuff-json-support
Run this project and try
```bash
curl -X POST http://localhost:8080/grpc-json/GreeterService/sayhello \
-H "Content-Type: application/json" \
-d '{"name": "YourName"}'
```
And you'll see
```text
{
  "greeting": "Hello, YourName"
}
```


