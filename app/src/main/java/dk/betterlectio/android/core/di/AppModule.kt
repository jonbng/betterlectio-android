package dk.betterlectio.android.core.di

import android.content.Context
import androidx.room.Room
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dk.betterlectio.android.core.cache.EntityOfflineStore
import dk.betterlectio.android.feature.directory.RateLimitedAvatarLoader
import dk.betterlectio.android.feature.offline.OfflineDatabase
import dk.betterlectio.android.feature.supabase.SupabaseConfig
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

/**
 * App-wide non-Lectio bindings.
 * Lectio networking lives in [dk.betterlectio.android.core.lectio.di.LectioModule].
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppContext(@ApplicationContext context: Context): Context = context

    @Provides
    @Singleton
    fun provideSupabaseConfig(): SupabaseConfig = SupabaseConfig.fromBuildConfig()

    @Provides
    @Singleton
    fun provideRateLimitedAvatarLoader(): RateLimitedAvatarLoader = RateLimitedAvatarLoader()

    @Provides
    @Singleton
    fun provideOfflineDatabase(@ApplicationContext context: Context): OfflineDatabase =
        Room.databaseBuilder(context, OfflineDatabase::class.java, "betterlectio_offline.db")
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    /**
     * Coil ImageLoader that rate-limits Lectio GetImage / avatar fetches.
     */
    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context,
        rateLimiter: RateLimitedAvatarLoader,
    ): ImageLoader {
        val rateLimitInterceptor = Interceptor { chain ->
            val request = chain.request()
            val host = request.url.host
            val path = request.url.encodedPath
            val isAvatar =
                host.contains("lectio.dk") &&
                    (path.contains("GetImage", ignoreCase = true) ||
                        request.url.toString().contains("pictureid", ignoreCase = true))
            if (isAvatar) {
                var attempts = 0
                while (!rateLimiter.acquire() && attempts < 40) {
                    Thread.sleep(25)
                    attempts++
                }
                try {
                    chain.proceed(request)
                } finally {
                    rateLimiter.release()
                }
            } else {
                chain.proceed(request)
            }
        }
        val okHttp = OkHttpClient.Builder()
            .addInterceptor(rateLimitInterceptor)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
        return ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { okHttp }))
            }
            .build()
    }

    @Provides
    @Singleton
    @Named("entityOffline")
    fun provideEntityOfflineStore(@ApplicationContext context: Context): EntityOfflineStore {
        val dir = File(context.filesDir, "entity_offline").apply { mkdirs() }
        return EntityOfflineStore(
            backend = object : EntityOfflineStore.DiskBackend {
                private fun file(key: String) =
                    File(dir, key.replace(Regex("[^a-zA-Z0-9._-]"), "_") + ".txt")

                override fun write(key: String, value: String) {
                    file(key).writeText(value)
                }

                override fun read(key: String): String? {
                    val f = file(key)
                    return if (f.exists()) f.readText() else null
                }

                override fun delete(key: String) {
                    file(key).delete()
                }

                override fun clear() {
                    dir.listFiles()?.forEach { it.delete() }
                }
            },
        )
    }
}
