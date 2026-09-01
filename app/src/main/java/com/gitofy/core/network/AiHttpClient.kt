package com.gitofy.core.network

import javax.inject.Qualifier

/** Dedicated AI transport marker; deliberately has no GitHub auth interceptors. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AiHttpClient
