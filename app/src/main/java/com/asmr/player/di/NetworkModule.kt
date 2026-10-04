package com.asmr.player.di

import com.asmr.player.BuildConfig
import com.asmr.player.data.local.DeviceIdentityStore
import com.asmr.player.data.remote.api.AsmrMirrorApi
import com.asmr.player.data.remote.api.AsmrOneApi
import com.asmr.player.data.remote.auth.DlsiteAuthStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Interceptor
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton
import javax.inject.Named

import com.asmr.player.data.remote.TrafficStatsInterceptor
import com.asmr.player.data.remote.NetworkHeaders
import com.asmr.player.data.remote.NetworkRouteManager
import com.asmr.player.util.MessageManager
import com.asmr.player.util.ASMR_ONE_SITE_FAILURE_MESSAGE
import com.asmr.player.util.DEEPSEEK_HTTP_CLIENT
import com.asmr.player.util.DEEPSEEK_TRANSLATION_CONCURRENCY
import com.asmr.player.util.DlsiteAntiHotlink
import com.google.gson.Gson
import java.io.IOException
import java.util.concurrent.TimeUnit

private val ASMR_ONE_SITE_DOMAINS = setOf("asmr.one", "asmr-100.com", "asmr-200.com", "asmr-300.com")

internal fun createDeepSeekDispatcher(): Dispatcher = Dispatcher().apply {
    maxRequests = DEEPSEEK_TRANSLATION_CONCURRENCY
    maxRequestsPerHost = DEEPSEEK_TRANSLATION_CONCURRENCY
}

internal fun isAsmrOneSiteRequest(host: String, encodedPath: String): Boolean {
    val normalizedHost = host.trim().lowercase()
    return ASMR_ONE_SITE_DOMAINS.any { domain ->
        normalizedHost == domain || normalizedHost.endsWith(".$domain")
    } || encodedPath.startsWith("/api/asmr-one/")
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()

    @Provides
    @Singleton
    fun provideDlsiteAuthStore(
        @ApplicationContext context: android.content.Context
    ): DlsiteAuthStore = DlsiteAuthStore(context)

    @Provides
    @Singleton
    fun provideOkHttpClient(
        messageManager: MessageManager,
        trafficStatsInterceptor: TrafficStatsInterceptor,
        deviceIdentityStore: DeviceIdentityStore,
        networkRouteManager: NetworkRouteManager
    ): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            redactHeader("Authorization")
            redactHeader("Proxy-Authorization")
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val asmrHeaders = Interceptor { chain ->
            val request = chain.request()
            val host = request.url.host.lowercase()
            val isAsmrOneRequest = isAsmrOneSiteRequest(host, request.url.encodedPath)
            val suppressAutoErrorMessage =
                request.header(NetworkHeaders.HEADER_SILENT_IO_ERROR) == NetworkHeaders.SILENT_IO_ERROR_ON
            
            val builder = request.newBuilder()
                .header("User-Agent", NetworkHeaders.USER_AGENT)

            if (isEaraBackendHost(host)) {
                builder.header(NetworkHeaders.HEADER_EARA_DEVICE_ID, deviceIdentityStore.getOrCreateDeviceId())
            }

            if (suppressAutoErrorMessage) {
                builder.removeHeader(NetworkHeaders.HEADER_SILENT_IO_ERROR)
            }

            if (host.contains("asmr.one")) {
                builder.header("Origin", NetworkHeaders.ORIGIN_ASMR_ONE)
                builder.header("Referer", NetworkHeaders.REFERER_ASMR_ONE)
            } else if (host.contains("asmr-100.com") || host.contains("asmr-200.com") || host.contains("asmr-300.com")) {
                builder.header("Origin", NetworkHeaders.ORIGIN_ASMR_ONE)
                builder.header("Referer", NetworkHeaders.REFERER_ASMR_ONE)
            } else if (host.contains("dlsite.")) {
                if (request.header("Referer") == null) {
                    builder.header("Referer", NetworkHeaders.REFERER_DLSITE)
                }
            } else if (host.contains("byteair.volces.com")) {
                if (request.header("Referer") == null) {
                    builder.header("Referer", NetworkHeaders.REFERER_DLSITE)
                }
            }
            
            try {
                val response = chain.proceed(builder.build())
                if (!response.isSuccessful && !suppressAutoErrorMessage) {
                    if (isAsmrOneRequest && response.code != 404) {
                        messageManager.showError(ASMR_ONE_SITE_FAILURE_MESSAGE)
                    } else {
                        when (response.code) {
                            401 -> messageManager.showError("认证已过期，请重新登录")
                            403 -> messageManager.showError("访问被拒绝")
                            404 -> {} // 忽略 404
                            500, 502, 503, 504 -> messageManager.showError("服务器开小差了，请稍后重试")
                            else -> {}
                        }
                    }
                }
                response
            } catch (e: IOException) {
                val canceled = runCatching { chain.call().isCanceled() }.getOrDefault(false)
                val canceledByMessage = e.message?.contains("canceled", ignoreCase = true) == true
                if (!suppressAutoErrorMessage && !canceled && !canceledByMessage) {
                    messageManager.showError(
                        if (isAsmrOneRequest) {
                            ASMR_ONE_SITE_FAILURE_MESSAGE
                        } else {
                            "网络连接失败，请检查网络"
                        }
                    )
                }
                throw e
            }
        }
        val client = OkHttpClient.Builder()
            .proxySelector(networkRouteManager.proxySelector)
            .proxyAuthenticator(networkRouteManager.proxyAuthenticator)
            .dns(networkRouteManager.dns)
            .connectTimeout(15_000L, TimeUnit.MILLISECONDS)
            .readTimeout(30_000L, TimeUnit.MILLISECONDS)
            .writeTimeout(30_000L, TimeUnit.MILLISECONDS)
            .addInterceptor(asmrHeaders)
            .addInterceptor(trafficStatsInterceptor)
            .addInterceptor(logging)
            .build()
        return networkRouteManager.register(client)
    }

    @Provides
    @Singleton
    @Named(DEEPSEEK_HTTP_CLIENT)
    fun provideDeepSeekOkHttpClient(okHttpClient: OkHttpClient): OkHttpClient =
        okHttpClient.newBuilder()
            .dispatcher(createDeepSeekDispatcher())
            .build()

    @Provides
    @Singleton
    @Named("image")
    fun provideImageOkHttpClient(
        trafficStatsInterceptor: TrafficStatsInterceptor,
        deviceIdentityStore: DeviceIdentityStore,
        networkRouteManager: NetworkRouteManager
    ): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            redactHeader("Authorization")
            redactHeader("Proxy-Authorization")
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val headers = Interceptor { chain ->
            val request = chain.request()
            val host = request.url.host.lowercase()

            val builder = request.newBuilder()
                .header("User-Agent", NetworkHeaders.USER_AGENT)

            if (isEaraBackendHost(host)) {
                builder.header(NetworkHeaders.HEADER_EARA_DEVICE_ID, deviceIdentityStore.getOrCreateDeviceId())
            }

            if (host.contains("asmr.one")) {
                builder.header("Origin", NetworkHeaders.ORIGIN_ASMR_ONE)
                builder.header("Referer", NetworkHeaders.REFERER_ASMR_ONE)
            } else if (host.contains("asmr-100.com") || host.contains("asmr-200.com") || host.contains("asmr-300.com")) {
                builder.header("Origin", NetworkHeaders.ORIGIN_ASMR_ONE)
                builder.header("Referer", NetworkHeaders.REFERER_ASMR_ONE)
            } else {
                val dlsiteHeaders = DlsiteAntiHotlink.headersForImageUrl(request.url.toString())
                dlsiteHeaders.forEach { (k, v) ->
                    if (request.header(k) == null) builder.header(k, v)
                }
            }

            chain.proceed(builder.build())
        }

        val dispatcher = Dispatcher().apply {
            maxRequests = 32
            maxRequestsPerHost = 2
        }

        val client = OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .proxySelector(networkRouteManager.proxySelector)
            .proxyAuthenticator(networkRouteManager.proxyAuthenticator)
            .dns(networkRouteManager.dns)
            .addInterceptor(headers)
            .addInterceptor(trafficStatsInterceptor)
            .addInterceptor(logging)
            .build()
        return networkRouteManager.register(client)
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(AsmrOneApi.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideAsmrOneApi(retrofit: Retrofit): AsmrOneApi {
        return retrofit.create(AsmrOneApi::class.java)
    }

    private fun mirrorApi(okHttpClient: OkHttpClient, baseUrl: String): AsmrMirrorApi =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AsmrMirrorApi::class.java)

    @Provides
    @Singleton
    @Named("asmr100")
    fun provideAsmr100MirrorApi(okHttpClient: OkHttpClient): AsmrMirrorApi =
        mirrorApi(okHttpClient, AsmrMirrorApi.BASE_URL_100)

    @Provides
    @Singleton
    @Named("asmr200")
    fun provideAsmr200MirrorApi(okHttpClient: OkHttpClient): AsmrMirrorApi =
        mirrorApi(okHttpClient, AsmrMirrorApi.BASE_URL_200)

    @Provides
    @Singleton
    @Named("asmr300")
    fun provideAsmr300MirrorApi(okHttpClient: OkHttpClient): AsmrMirrorApi =
        mirrorApi(okHttpClient, AsmrMirrorApi.BASE_URL_300)

    private val earaBackendHost: String?
        get() = BuildConfig.LISTEN_TOGETHER_BASE_URL
            .toHttpUrlOrNull()
            ?.host
            ?.lowercase()

    private fun isEaraBackendHost(host: String): Boolean {
        return earaBackendHost?.let { host == it } == true
    }
}
