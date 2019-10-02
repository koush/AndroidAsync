# Kotlin Coroutines

Adds coroutines support to AndroidAsync and Ion.

Since AndroidAsync and Ion operations all returned futures, you can simply call await() on them within a Kotlin suspend function.

```kotlin
val data = Ion.with(context).load("https://google.com/robots.txt").asString().await()
```

That's it!
