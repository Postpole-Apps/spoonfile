package com.postpole.spoonfile.di

import javax.inject.Qualifier

/** Qualifier for the injected IO [kotlinx.coroutines.CoroutineDispatcher] (file and image work). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher
