package com.sksamuel.hoplite.watch

import com.sksamuel.hoplite.ConfigLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * A [ReloadableConfig] accepts a [ConfigLoader] and a target config [KClass].
 *
 * One or more [Watchable]s can be added to this class, and when they trigger an update,
 * the config is reloaded.
 *
 * You can retrieve the latest config at any time with [getLatest].
 *
 * Errors thrown during the config loading process are handled by an [errorHandler] which
 * can be set via [withErrorHandler].
 */
class ReloadableConfig<A : Any>(
  private val configLoader: ConfigLoader,
  private val clazz: KClass<A>
) {

  private val config = AtomicReference(configLoader.loadConfigOrThrow(clazz, emptyList()))
  private val scope = CoroutineScope(Dispatchers.Default)
  private var errorHandler: ((Throwable) -> Unit)? = null
  private val subscribers = ConcurrentHashMap.newKeySet<(A) -> Unit>()

  fun addWatcher(watchable: Watchable): ReloadableConfig<A> {
    scope.launch {
      watchable.watch(
        { reloadConfig() },
        { errorHandler?.invoke(it) }
      )
    }
    return this
  }

  /**
   * Add a watcher that refreshes this config at a fixed duration.
   */
  fun addInterval(interval: Duration): ReloadableConfig<A> =
    addWatcher(FixedIntervalWatchable(interval.inWholeMilliseconds))

  /**
   * Add a watcher that refreshes this config on a fixed interval.
   */
  fun addInterval(millis: Long): ReloadableConfig<A> = addInterval(millis.milliseconds)

  @Deprecated("Use withErrorHandler", ReplaceWith("withErrorHandler(handler)"))
  fun addErrorHandler(handler: (Throwable) -> Unit): ReloadableConfig<A> = withErrorHandler(handler)

  fun withErrorHandler(handler: (Throwable) -> Unit): ReloadableConfig<A> {
    errorHandler = handler
    return this
  }

  private fun reloadConfig() {
    runCatching { configLoader.loadConfigOrThrow(clazz, emptyList()) }.fold(
      { update(it) },
      { errorHandler?.invoke(it) }
    )
  }

  private fun update(a: A) {
    config.set(a)
    subscribers.forEach { it.invoke(a) }
  }

  fun getLatest(): A {
    return config.get()
  }

  fun subscribe(subscriber: (A) -> Unit) {
    subscribers.add(subscriber)
  }
}
