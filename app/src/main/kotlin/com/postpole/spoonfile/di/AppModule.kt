package com.postpole.spoonfile.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.postpole.spoonfile.analytics.Analytics
import com.postpole.spoonfile.analytics.createAnalytics
import com.postpole.spoonfile.review.ReviewPolicy
import com.postpole.spoonfile.review.ReviewPrompter
import com.postpole.spoonfile.billing.BillingGateway
import com.postpole.spoonfile.billing.BillingRepository
import com.postpole.spoonfile.billing.PlayBillingGateway
import com.postpole.spoonfile.billing.ProStatus
import com.postpole.spoonfile.data.AppDatabase
import com.postpole.spoonfile.data.CategoryDao
import com.postpole.spoonfile.data.ItemDao
import com.postpole.spoonfile.data.RecipeDao
import com.postpole.spoonfile.data.ShoppingDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "app.db").build()

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    fun provideItemDao(db: AppDatabase): ItemDao = db.itemDao()

    @Provides
    fun provideRecipeDao(db: AppDatabase): RecipeDao = db.recipeDao()

    @Provides
    fun provideCategoryDao(db: AppDatabase): CategoryDao = db.categoryDao()

    @Provides
    fun provideShoppingDao(db: AppDatabase): ShoppingDao = db.shoppingDao()

    @Provides
    @Singleton
    fun provideBillingGateway(@ApplicationContext context: Context): BillingGateway =
        PlayBillingGateway(context)

    @Provides
    @Singleton
    fun provideBillingRepository(
        gateway: BillingGateway,
        dataStore: DataStore<Preferences>,
        analytics: Analytics,
    ): BillingRepository = BillingRepository(
        gateway = gateway,
        dataStore = dataStore,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        analytics = analytics,
    )

    @Provides
    fun provideProStatus(billing: BillingRepository): ProStatus = object : ProStatus {
        override val isPro = billing.isPro
    }

    @Provides
    @Singleton
    fun provideAnalytics(@ApplicationContext context: Context): Analytics = createAnalytics(context)

    @Provides
    fun provideReviewPrompter(policy: ReviewPolicy): ReviewPrompter = policy

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }
}
