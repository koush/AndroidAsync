# Support for Kotlin Coroutines in AndroidAsync and Ion

Adds coroutines support to AndroidAsync and [Ion](https://github.com/koush/ion).

Maven:
```xml
<dependency>
    <groupId>com.koushikdutta.async</groupId>
    <artifactId>androidasync-kotlin</artifactId>
    <version>(insert latest version)</version>
</dependency>
```

Gradle:
```groovy
dependencies {
    compile 'com.koushikdutta.async:androidasync-kotlin:<insert latest version>'
}
```

Since AndroidAsync and Ion operations all returned futures, you can simply call await() on them within a Kotlin suspend function.

```kotlin
suspend fun getTheRobotsTxt() {
  val googleRobots = Ion.with(context)
  .load("https://google.com/robots.txt")
  .asString()
  .await()

  val githubRobots = Ion.with(context)
  .load("https://github.com/robots.txt")
  .asString()
  .await()

  return googleRobots + githubRobots
}
```

That's it!

But remember that the await() suspends, so if you want to fetch both robots.txt at the same time:

```kotlin
suspend fun getTheRobotsTxt() {
  val googleRobots = Ion.with(context)
  .load("https://google.com/robots.txt")
  .asString()

  val githubRobots = Ion.with(context)
  .load("https://github.com/robots.txt")
  .asString()

  return googleRobots.await() + githubRobots.await()
}
```

