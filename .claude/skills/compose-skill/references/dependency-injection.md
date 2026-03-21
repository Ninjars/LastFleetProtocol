# Dependency Injection in Compose Projects

Shared DI guidance for Jetpack Compose and Compose Multiplatform. For framework-specific setup,
see [Kotlin Inject](kotlin-inject.md), [Koin](koin.md), or [Hilt](hilt.md).

References:

- [Kotlin Inject](kotlin-inject.md) — compile-time DI for KMP, components/scopes/qualifiers, testing
- [Koin](koin.md) — runtime-first DI with optional KSP, Nav 3 integration, scopes, testing
- [Hilt](hilt.md) — Android-only DI with strong lifecycle integration and instrumented testing

## When to Use Hilt vs Koin vs Kotlin Inject

| Criterion               | Kotlin Inject                               | Koin                                                             | Hilt                                         |
|-------------------------|---------------------------------------------|------------------------------------------------------------------|----------------------------------------------|
| Platform                | Multiplatform (Android, iOS, Desktop, JVM)  | Multiplatform (Android, iOS, Desktop, Web)                       | Android-only                                 |
| Dependency resolution   | Compile-time (KSP)                          | Runtime (DSL) or compile-time (Koin Annotations + KSP)           | Compile-time                                 |
| Error detection         | Build-time                                  | Runtime by default; improved with `verify()` and KSP annotations | Build-time                                   |
| Setup complexity        | Moderate (KSP setup, explicit graph design) | Low-to-moderate (DSL-first; optional annotations)                | Higher (Gradle plugin + Android annotations) |
| Compose Multiplatform   | Full support                                | Full support                                                     | Not supported                                |
| Compose injection style | Explicit graph/factory wiring               | `koinInject()` / `koinViewModel()` helpers                       | `hiltViewModel()` + Android entry points     |

**Default recommendation:**

- **Compose Multiplatform projects**: prefer **Kotlin Inject** as default. Use Koin when the team
  explicitly prefers its runtime DSL ergonomics.
- **Android-only projects**: **Hilt** is the default recommendation. Koin or Kotlin Inject are valid
  alternatives when team constraints or migration plans justify them.

For detailed setup, modules, scoping, and testing, see dedicated
references: [kotlin-inject.md](kotlin-inject.md), [koin.md](koin.md), and [hilt.md](hilt.md). This
file stays focused on framework decision — do not duplicate implementation details here.

## Shared DI Concepts

These principles apply regardless of framework choice:

### Constructor injection as the default

Always inject dependencies through the constructor. Field injection (`@Inject lateinit var`) couples
the class to the DI framework and makes testing harder.

### Interface-based design

Bind interfaces to implementations — repositories, data sources, and platform services should be
defined as interfaces. This enables swapping implementations in tests without mocking the DI
framework.

```kotlin
// Define interface
interface UserRepository {
    suspend fun getUser(id: String): User
}

// Bind implementation via DI
// Kotlin Inject: constructor injection + interface-returning @Provides when explicit mapping is required
// Koin: single<UserRepository> { UserRepositoryImpl(get()) }
// Hilt: @Binds abstract fun bind(impl: UserRepositoryImpl): UserRepository
```

### Scope lifecycle alignment

| Scope                   | When to use                                | Examples                                 |
|-------------------------|--------------------------------------------|------------------------------------------|
| Singleton               | Lives for app lifetime                     | API client, database, analytics          |
| Activity-retained       | Survives config changes (Android-specific) | User session, auth state                 |
| ViewModel-scoped        | Tied to a feature screen                   | Feature-specific calculators, validators |
| Factory (new each time) | Stateless or short-lived                   | Formatters, mappers                      |

Over-scoping wastes memory; under-scoping creates redundant instances. Match the scope to the
dependency's actual lifetime.

### Module organization

Organize DI modules by feature, not by type. Each feature module declares its own dependencies:

```text
feature-estimate/
    EstimateModule        → repository, calculator, validator, ViewModel
feature-settings/
    SettingsModule        → repository, ViewModel
core/
    CoreModule            → API client, database, platform bindings
```

Combine feature modules in the app module. Platform-specific bindings go in platform modules (
`androidMain`, `iosMain`).

### Testing principle

Swap real implementations with fakes via DI configuration — do not mock the DI framework itself.

- **Kotlin Inject**: constructor-first tests; optional component creation smoke tests
- **Koin**: `appModule.verify()` for graph verification, module overrides in tests
- **Hilt**: `@TestInstallIn` to replace modules, `hilt-android-testing` for instrumented tests

For ViewModel unit testing (framework-agnostic), see [testing.md](testing.md).
