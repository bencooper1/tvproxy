package com.tvproxy.app.core.di

import javax.inject.Qualifier

/**
 * Coroutine dispatcher qualifiers (architecture.md §3 `core/di`).
 * Production binds the standard dispatchers; tests inject unconfined ones.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MainDispatcher
