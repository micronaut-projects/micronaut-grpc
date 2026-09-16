from jakarta.inject import Singleton


@Singleton
class GreetingService:

    def say_hello(self, name: str) -> str:
        return f"Hello {name}"
