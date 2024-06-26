@file:Suppress("ktlint:standard:no-wildcard-imports")

package com.hungrybrothers.abletotrip.ui.viewmodel

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.hungrybrothers.abletotrip.ui.network.KtorClient.client
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.invoke
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class NavigationViewModel : ViewModel() {
    private val _departureData = MutableLiveData<Resource<LatLng>>()
    val departureData: LiveData<Resource<LatLng>> = _departureData

    private val _arrivalData = MutableLiveData<Resource<LatLng>>()
    val arrivalData: LiveData<Resource<LatLng>> = _arrivalData

    private val _navigationData = MutableLiveData<Resource<NavigationData>>()
    val navigationData: LiveData<Resource<NavigationData>> = _navigationData

    val polylineDataList = MutableLiveData<List<PolylineData>>()

    val walkDataList1 = MutableLiveData<PolylineData>()

    val walkDataList2 = MutableLiveData<PolylineData>()

    private val _duration = MutableLiveData<Int>()
    val duration: LiveData<Int> = _duration

    private val _detailRouteInfo = MutableLiveData<List<DetailRouteInfo>>()
    val detailRouteInfo: LiveData<List<DetailRouteInfo>> = _detailRouteInfo

    private val _messageInfo = MutableLiveData<String?>("")
    val messageInfo: LiveData<String?> = _messageInfo

    private val _errorMessageInfo = MutableLiveData<String?>(null)
    val errorMessageInfo: LiveData<String?> = _errorMessageInfo

    fun fetchNavigationData(
        departure: String?,
        arrival: String?,
    ) {
        // LiveData 초기화
        _navigationData.value = Resource.loading(null) // 로딩 상태로 초기화
        _departureData.value = Resource.loading(null)
        _arrivalData.value = Resource.loading(null)
        _duration.value = 0 // 초기값을 0 또는 적절한 기본값으로 설정
        _detailRouteInfo.value = emptyList() // 빈 리스트로 초기화
        polylineDataList.value = emptyList()
        walkDataList1.value = PolylineData(emptyList(), Color.Blue)
        walkDataList2.value = PolylineData(emptyList(), Color.Blue)
        _messageInfo.value = null
        _errorMessageInfo.value = null

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val requestBody =
                    buildJsonObject {
                        put("departure", departure ?: "")
                        put("arrival", arrival ?: "")
                    }
                val response: HttpResponse =
                    client.post("http://k10a607.p.ssafy.io:8087/navigation/search-direction/") {
                        contentType(ContentType.Application.Json)
                        setBody(requestBody)
                    }
                if (response.status == HttpStatusCode.OK) {
                    val responseBody = response.bodyAsText()
                    val data = Json { ignoreUnknownKeys = true }.decodeFromString<NavigationData>(responseBody)
                    _navigationData.postValue(Resource.success(data))

                    println("navigationData check : $responseBody")

                    _departureData.postValue(
                        Resource.success(
                            data.polyline_info.firstOrNull()?.info?.firstOrNull()?.let { info ->
                                LatLng(
                                    info.latitude ?: error("Latitude is null"),
                                    info.longitude ?: error("Longitude is null"),
                                )
                            } ?: error("No polyline info available"),
                        ),
                    )
                    _arrivalData.postValue(
                        Resource.success(
                            data.polyline_info.lastOrNull()?.info?.lastOrNull()?.let { info ->
                                LatLng(
                                    info.latitude ?: error("Latitude is null"),
                                    info.longitude ?: error("Longitude is null"),
                                )
                            } ?: error("No polyline info available"),
                        ),
                    )
                    _duration.postValue(data.duration)
                    _detailRouteInfo.postValue(data.detail_route_info)
                    _messageInfo.postValue(data.message)

                    val polylineData =
                        data.polyline_info.flatMap { polylineInfo ->
                            polylineInfo.info.map { info ->
                                async {
                                    val color =
                                        when (polylineInfo.type) {
                                            "subway" -> {
                                                val line = info.line ?: ""
                                                subwayColor[line] ?: Color.Red
                                            }
                                            else -> Color.Green
                                        }

                                    val points =
                                        if (info.polyline != null) {
                                            val decodedPoints = fetchPolylineData(info.polyline).data
                                            decodedPoints.map { LatLng(it[0], it[1]) }
                                        } else {
                                            emptyList()
                                        }
                                    PolylineData(points, color)
                                }
                            }
                        }.awaitAll().filterNot { it.points.isEmpty() }
                    val walkoneData =
                        data.polyline_info
                            .firstOrNull { it.type == "walk" }
                            ?.info
                            ?.map { info ->
                                LatLng(info.latitude ?: 37.501286, info.longitude ?: 127.0396029)
                            }
                            ?: emptyList()
                    val walktwoData =
                        data.polyline_info
                            .lastOrNull { it.type == "walk" }
                            ?.info
                            ?.map { info ->
                                LatLng(info.latitude ?: 37.501286, info.longitude ?: 127.0396029)
                            }
                            ?: emptyList()
                    withContext(Dispatchers.Main) {
                        walkDataList1.postValue(PolylineData(walkoneData, Color.Blue))
                        walkDataList2.postValue(PolylineData(walktwoData, Color.Blue))
                        polylineDataList.postValue(polylineData)
                    }
                } else {
                    val responseBody = response.bodyAsText()

                    // JSON 응답 파싱
                    val errorData = Json { ignoreUnknownKeys = true }.decodeFromString<ErrorData>(responseBody)
                    println("Error message from server: ${errorData.message}")

                    _errorMessageInfo.postValue(errorData.message)

                    _navigationData.postValue(
                        Resource.error(
                            "Failed to load data: HTTP ${response.status.value}",
                            null,
                        ),
                    )
                }
            } catch (e: Exception) {
                _navigationData.postValue(Resource.error("Error occurred: ${e.message}", null))
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        client.close()
    }
}

data class Resource<out T>(val status: Status, val data: T?, val message: String?) {
    companion object {
        fun <T> success(data: T): Resource<T> = Resource(Status.SUCCESS, data, null)

        fun <T> error(
            msg: String,
            data: T?,
        ): Resource<T> = Resource(Status.ERROR, data, msg)

        fun <T> loading(data: T?): Resource<T> = Resource(Status.LOADING, data, null)
    }

    enum class Status {
        SUCCESS,
        ERROR,
        LOADING,
    }
}

@Serializable
data class NavigationData(
    val message: String,
    val duration: Int,
    val is_subway_exist: Boolean,
    val polyline_info: List<PolylineInfo>,
    val detail_route_info: List<DetailRouteInfo>,
)

@Serializable
data class PolylineInfo(
    val type: String,
    val info: List<Info>,
)

@Serializable
data class Info(
    val longitude: Double? = null,
    val latitude: Double? = null,
    val line: String? = null,
    val polyline: String? = null,
)

@Serializable
data class DetailRouteInfo(
    val type: String,
    val info: List<String>,
)

@Serializable
data class PolylineResponse(
    val success: Int,
    val data: List<List<Double>>,
)

data class PolylineData(
    val points: List<LatLng>,
    val color: Color,
)

@Serializable
data class ErrorData(
    val message: String,
)

suspend fun fetchPolylineData(incodedpolyline: String?): PolylineResponse {
    try {
        val requestBody =
            buildJsonObject {
                put("input", incodedpolyline)
            }
        val response =
            client.post("http://k10a607.p.ssafy.io:8087/navigation/polyline/") {
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }
        val responseBody = response.bodyAsText()
        val data = Json { ignoreUnknownKeys = true }.decodeFromString<PolylineResponse>(responseBody)
        return data
    } catch (e: Exception) {
        throw e
    }
}

val subwayColor =
    mutableMapOf(
        "1호선" to Color(0xFF002060),
        "2호선" to Color(0xFF00AF4F),
        "3호선" to Color(0xFFFF9900),
        "4호선" to Color(0xFF0099FF),
        "5호선" to Color(0xFF9160AC),
        "6호선" to Color(0xFFA06134),
        "7호선" to Color(0xFF77C000),
        "8호선" to Color(0xFFEC008C),
        "9호선" to Color(0xFFB1A152),
    )
