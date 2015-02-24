        Stetho.initialize(
        Stetho.newInitializerBuilder(this)
        .enableDumpapp(Stetho.defaultDumperPluginsProvider(this))
        .enableWebKitInspector(Stetho.defaultInspectorModulesProvider(this))
        .build());
        Ion.getDefault(this).getHttpClient().getMiddleware()
        .add(new StethoMiddleware());