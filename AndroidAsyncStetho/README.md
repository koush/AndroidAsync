# AndroidAsync and Ion Stetho support

[Stetho](https://github.com/facebook/stetho) is a tool that lets you log and view your network requests and more on Android.

### Using Stetho with Ion

```java
// typical initialization of Stetho
Stetho.initialize(
Stetho.newInitializerBuilder(this)
.enableDumpapp(Stetho.defaultDumperPluginsProvider(this))
.enableWebKitInspector(Stetho.defaultInspectorModulesProvider(this))
.build());

Ion.getDefault(this).getHttpClient().getMiddleware()
.add(new StethoMiddleware());
```

### Using Stetho with AndroidAsync

```java
// typical initialization of Stetho
Stetho.initialize(
Stetho.newInitializerBuilder(this)
.enableDumpapp(Stetho.defaultDumperPluginsProvider(this))
.enableWebKitInspector(Stetho.defaultInspectorModulesProvider(this))
.build());

AsyncHttpClient.getDefaultInstance().getMiddleware()
.add(new StethoMiddleware());
```
