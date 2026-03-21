# Dependency Injection with Kotlin Inject

Kotlin Inject is the preferred DI framework for Compose Multiplatform projects in this codebase. It provides compile-time DI with KMP support via KSP code generation.

For Kotlin Inject vs Hilt vs Koin decision guidance and shared DI concepts, see [dependency-injection.md](dependency-injection.md). For Koin alternatives, see [koin.md](koin.md). For Android-only Hilt, see [hilt.md](hilt.md).

References:
- [Kotlin Inject GitHub](https://github.com/evant/kotlin-inject)
- [Anvil for Kotlin Inject (optional module tooling)](https://github.com/amzn/kotlin-inject-anvil)
- [KSP docs](https://kotlinlang.org/docs/ksp-overview.html)

## Table of Contents

- [Setup](#setup)
- [Components, Providers, Scopes, and Qualifiers](#components-providers-scopes-and-qualifiers)
- [Component Creation](#component-creation)
- [Constructor Injection Patterns](#constructor-injection-patterns)
- [Compose and ViewModel Integration](#compose-and-viewmodel-integration)
- [Navigation Integration](#navigation-integration)
- [Kotlin Inject in MVI](#kotlin-inject-in-mvi)
- [Testing](#testing)
- [Anti-Patterns](#anti-patterns)

## Setup

### Gradle configuration (KMP)

```kotlin
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.ksp)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlininject.runtime)
        }
    }
}

dependencies {
    kspCommonMainMetadata(libs.kotlininject.compiler)
    add("kspJvm", libs.kotlininject.compiler)
    add("kspAndroid", libs.kotlininject.compiler)
    // add("kspIosX64", libs.kotlininject.compiler)
    // add("kspIosArm64", libs.kotlininject.compiler)
    // add("kspIosSimulatorArm64", libs.kotlininject.compiler)
}
```

Kotlin Inject is compile-time DI: missing bindings are surfaced at build time through generated component code.

## Components, Providers, Scopes, and Qualifiers

### Component root

```kotlin
@Singleton
@Component
abstract class AppComponent(
    private val enableLogging: Boolean,
) {
    abstract val navHost: LFNavHost
    abstract val gameStateHolder: GameStateHolder
}
```

`@Component` defines the graph root and exposes resolved top-level dependencies.

### Providers (`@Provides`)

Use `@Provides` when construction is not simple constructor injection (third-party factories, builder APIs, config adaptation):

```kotlin
@Singleton
@Provides
protected fun stateManager(): StateManager = StateManager.newInstance(
    isLoggingEnabled = enableLogging,
    instanceNameForLogging = Constants.GAME_LOG_TAG,
)
```

### Scope annotations

```kotlin
@Scope
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER)
annotation class Singleton
```

Apply scope at the component and binding level for shared instances.

### Qualifiers

Use qualifiers when there are multiple bindings of the same type:

```kotlin
@Qualifier
@Target(
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.TYPE,
)
annotation class Named(val value: String)
```

Example: `@Named("background") Kubriko` and `@Named("game") Kubriko`.

## Component Creation

KSP generates `Inject<AppComponentName>`. Prefer creating the graph through the generated extension:

```kotlin
val component = AppComponent::class.create(enableLogging = true)
```

Create once at app startup and pass dependencies downward explicitly (root composable, navigation host, screen factories).

## Constructor Injection Patterns

### Default rule

Use constructor injection for app classes whenever possible:

```kotlin
class GameStateManager(
    private val stateManager: StateManager,
    private val actorManager: ActorManager,
    private val viewportManager: ViewportManager,
)
```

### When to use `@Provides`

Use providers for:
- Third-party types you cannot annotate.
- Builder/factory construction.
- Qualified variants of the same type.
- Primitive/config adaptation at graph boundaries.

### Interface bindings

Prefer constructor-injected implementation classes and expose interface-typed consumers. If explicit mapping is needed, provide interface-returning provider functions.

## Compose and ViewModel Integration

Kotlin Inject does not require a Compose service-locator API like `koinInject()`. Prefer explicit factories or constructor parameters.

### Route-level factory pattern

```kotlin
@Composable
fun gameRoute(
    viewModelFactory: () -> GameVM,
) {
    val viewModel = remember(viewModelFactory) { viewModelFactory() }
    // collect state and render screen
}
```

### Compose root wiring

```kotlin
@Composable
fun appRoot(component: AppComponent) {
    component.navHost()
}
```

This keeps composables framework-agnostic and easy to test with fake factories.

## Navigation Integration

For Nav 3 or Nav 2, keep DI and routing loosely coupled:

- Build the dependency graph once at startup.
- Resolve feature dependencies at route boundaries.
- Pass resolved dependencies/factories into destination composables.
- Keep navigation declarations independent from DI implementation details.

## Kotlin Inject in MVI

MVI remains framework-agnostic. Kotlin Inject affects only wiring:

- Inject use cases/services into ViewModel constructors.
- Keep `StateFlow<State>`, intent handling, and side-effect channels independent of DI.
- Compose observes state and emits intents; no service-locator calls in UI.

```kotlin
class LandingVM(
    private val setMusicEnabled: SetMusicEnabledUseCase,
    private val setSoundEffectsEnabled: SetSoundEffectsEnabledUseCase,
) : LFViewModel<LandingState, LandingIntent, LandingSideEffect>()
```

## Testing

### Unit tests

Prefer direct constructor tests without DI container startup:

```kotlin
@Test
fun togglingMusicUpdatesState() = runTest {
    val vm = LandingVM(
        setMusicEnabled = FakeSetMusicEnabledUseCase(),
        setSoundEffectsEnabled = FakeSetSoundEffectsEnabledUseCase(),
    )
    // assert event -> state/effect behavior
}
```

### Graph tests

Compile-time generation catches most wiring errors. Add smoke tests for component creation if needed:

```kotlin
@Test
fun appComponentCreates() {
    val component = AppComponent::class.create(enableLogging = false)
    assertNotNull(component.navHost)
}
```

## Anti-Patterns

| Anti-pattern | Why it is harmful | Better approach |
| --- | --- | --- |
| Building component instances deep in UI tree | Multiple graphs, inconsistent lifetimes | Build once at app root and pass dependencies down |
| Using global mutable singletons outside DI | Hidden coupling, test fragility | Keep shared state in scoped DI bindings |
| Overusing `@Provides` for simple classes | Verbose graph, weaker readability | Prefer constructor injection |
| Injecting platform-only types directly in `commonMain` | Breaks KMP portability | Use expect/actual or platform boundary adapters |
| Treating ViewModels as DI singletons | Lifecycle mismatch | Create ViewModels per route/screen owner |
| Using DI lookups inside composables as hidden dependencies | Harder previews/tests, implicit coupling | Use explicit parameters and factories |
