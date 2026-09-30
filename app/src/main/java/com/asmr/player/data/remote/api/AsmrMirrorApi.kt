package com.asmr.player.data.remote.api

import com.asmr.player.data.remote.NetworkHeaders
import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * asmr-100/200/300 三个镜像站的统一 Retrofit 接口。
 * 三站共用同一套端点与参数，仅 BASE_URL 不同；search 与主站 AsmrOneApi 的差异：
 * order 默认 create_date、多 pageSize/includeTranslationWorks、返回简化的 Asmr200SearchResponse。
 * 实例由 NetworkModule 按 @Named("asmr100"/"asmr200"/"asmr300") 提供。
 */
interface AsmrMirrorApi : AsmrWorkApi {
    @GET("search/{keyword}")
    suspend fun search(
        @Path("keyword") keyword: String,
        @Query("page") page: Int = 1,
        @Query("order") order: String = "create_date",
        @Query("sort") sort: String = "desc",
        @Query("pageSize") pageSize: Int = 20,
        @Query("subtitle") subtitle: Int = 0,
        @Query("includeTranslationWorks") includeTranslationWorks: Boolean = true,
        @Header(NetworkHeaders.HEADER_SILENT_IO_ERROR) silentIoError: String? = null
    ): Asmr200SearchResponse

    companion object {
        const val BASE_URL_100 = "https://api.asmr-100.com/api/"
        const val BASE_URL_200 = "https://api.asmr-200.com/api/"
        const val BASE_URL_300 = "https://api.asmr-300.com/api/"
    }
}

data class Asmr200SearchResponse(
    val works: List<Asmr200Work> = emptyList()
)

data class Asmr200Work(
    val id: Int = 0,
    val source_id: String? = null,
    val title: String? = null,
    val duration: Int? = null,
    @SerializedName(value = "mainCoverUrl", alternate = ["main_cover_url", "main_cover_url_small", "main_cover_url_large"])
    val mainCoverUrl: String? = null,
    val dl_count: Int? = null,
    val price: Int? = null,
    val circle: Circle? = null,
    val name: String? = null,
    val vas: List<Artist>? = null,
    val tags: List<Tag>? = null,
    val original_workno: String? = null,
    val translation_info: AsmrOneTranslationInfo? = null,
    val language_editions: List<Asmr200LanguageEdition>? = null,
    @SerializedName("other_language_editions_in_db")
    val other_language_editions_in_db: List<AsmrOneOtherLanguageEditionInDb>? = null
)

data class Asmr200LanguageEdition(
    val lang: String? = null,
    val label: String? = null,
    val workno: String? = null
)
