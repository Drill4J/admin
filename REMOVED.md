# Removed functionality

## Lazy modules
Allow you to create Ktor modules in which you can set your own DI containers and authentication configuration.

[Pull Request](https://github.com/Drill4J/test2code-plugin)

Usage:

_MyModule.kt_
```kotlin
fun Application.installLazyModules() {
    install(LazyModules)
}

fun Application.myModule() = lazyModule {
    routing {
        val myService by closestDI().instance<MyService>()
        authentication("myAuth") {
            get("/my-service") {
                myService.call()
            }
            //other routes
        }
    }
}.withDI {
    bind<MyService>() with singleton { MyServiceImpl() }
    ... //other DI configs
}.withAuthentication {
    jwt("myAuth") {
        ... //jwt config
    }
}
```

_application.conf_
```json
ktor {
  application {
    modules = [
      com.example.MyModuleKt.installLazyModules,
      com.example.MyModuleKt.myModule, 
      ..., //other lazy modules
      com.epam.drill.admin.auth.module.LazyModulesKt.initLazyModules
    ]
  }
}
```

## OAuth2 token validation
Validates tokens received from OAuth2 without calling Authentication server.

[Pull Request](https://github.com/Drill4J/admin/pull/343)

Usage:
```
DRILL_OAUTH2_JWK_SET_URL=http://localhost:8080/realms/master/protocol/openid-connect/certs
```

