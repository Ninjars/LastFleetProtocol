# Project Instructions

## Code Style

- Use Kotlin for all new files
- Follow Material Design 3 guidelines
- Prefer Jetpack Compose for UI components

## Architecture

- This is a Kotlin Multiplatform project
- as much as possible, logic should be implemented within the :composeApp:commonMain module, or in a
  platform-agnostic submodule.
- Use MVI pattern with ViewModels, using the LFViewModel parent class to standardise the API
    - ViewModels provide a Flow<State> for the UI to consume
    - ViewModels consume Intents from the UI, which are scoped to that particular ViewModel
    - ViewModels produce SideEffects using a channel, which are used with the HandleSideEffect()
      composable function at the navigation layer
- Contain business logic in use cases, generally accessed from the ViewModel or other UseCases
- Use Ktor for network calls
- libs.versions.toml is used for managing dependency versioning

## Game Engine

- Kubriko is a real-time game engine that uses JetpackCompose for its UI
- a clone of the kubriko repo, with some examples, can be found at ~
  /Users/jez/dev/multiplatform/kubriko-main
- Docs and the current state of the project are also available online
  at https://github.com/pandulapeter/kubriko/tree/main/documentation

## App Design

- The project is a game
- There are a collection of relatively simple features/screens providing a framework for accessing
  the game - splash screen for loading, landing screen for quickly getting into the game, settings
  screen to manage settings and data
- Then there is the game feature, which has multiple sub-screens and a real-time simulation engine
- The game feature relies heavily on Kubriko for managing state and providing functionality such as
  sound playback

## Testing

- Write unit tests for ViewModels and use cases
- Use MockK for mocking
- Prefer property-based testing where applicable