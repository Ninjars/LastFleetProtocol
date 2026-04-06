package jez.lastfleetprotocol.prototype.di

import me.tatarka.inject.annotations.Qualifier
import me.tatarka.inject.annotations.Scope

@Scope
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER)
annotation class Singleton

@Qualifier
@Target(
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.TYPE,
)
annotation class Named(val value: String)

object DependencyName {
    const val KUBRIKO_GAME = "game"
    const val KUBRIKO_BACKGROUND = "background"
}
