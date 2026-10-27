/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hlcyn.updater.data.source.network

import android.content.Context
import android.util.Log
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonIgnoreUnknownKeys
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import okhttp3.OkHttpClient
import okhttp3.Request
import org.hlcyn.updater.R
import org.hlcyn.updater.deviceinfo.DeviceInfoUtils
import java.io.IOException
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonIgnoreUnknownKeys
data class ChangelogResponse(
    @SerialName("device") val device: String = "",
    @SerialName("build_date") val buildDate: String = "",
    @SerialName("version") val version: String = "",
    @SerialName("device_changelog") val deviceChangelog: List<String> = emptyList(),
    @SerialName("system_changelog") val systemChangelog: List<String> = emptyList(),
)

class UpdatesNetworkDataSource(private val context: Context) {
    private val serverUrl: String
        get() {
            val base = DeviceInfoUtils.updaterUri.trim().ifEmpty {
                context.getString(R.string.updater_server_url)
            }
            require(base.startsWith("https://")) {
                "Update server URL must use HTTPS: $base"
            }
            return base
                .replace("{device}", DeviceInfoUtils.device)
                .replace("{type}", DeviceInfoUtils.releaseType.lowercase())
                .replace("{version}", DeviceInfoUtils.halcyonVersion)
                .replace("{variant}", DeviceInfoUtils.variant.lowercase())
                .replace("{incr}", DeviceInfoUtils.buildVersionIncremental)
        }

    private val client = OkHttpClient.Builder()
        .callTimeout(10, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()

    fun fetchUpdates(): List<NetworkUpdate> {
        val request = Request.Builder()
            .url(serverUrl)
            .build()

        val responseBody = client.newCall(request).execute().use { response ->
            if (response.code == 404 || response.code == 204) {
                return emptyList()
            }
            if (!response.isSuccessful) {
                throw IOException("Unexpected HTTP status: ${response.code}")
            }

            response.body?.string() ?: throw IOException("Empty response body")
        }

        if (responseBody.isBlank()) {
            return emptyList()
        }

        val jsonElement = Json.parseToJsonElement(responseBody)
        return when (jsonElement) {
            is JsonArray -> Json.decodeFromJsonElement<List<NetworkUpdate>>(jsonElement)
            is JsonObject -> listOf(Json.decodeFromJsonElement<NetworkUpdate>(jsonElement))
            else -> emptyList()
        }
    }

    fun fetchChangelog(device: String, version: String, buildDate: String): ChangelogResponse? {
        val url = "https://canna.hlcyn.org/api/v2/ota/changelog/$device?version=$version&builddate=$buildDate"
        Log.d("UpdatesNetworkDataSource", "Fetching changelog from: $url")
        val request = Request.Builder()
            .url(url)
            .build()

        return try {
            val responseBody = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w("UpdatesNetworkDataSource", "Changelog fetch failed with code ${response.code} for URL: $url")
                    return null
                }
                response.body?.string() ?: return null
            }
            if (responseBody.isBlank()) return null
            Json.decodeFromString<ChangelogResponse>(responseBody)
        } catch (e: Exception) {
            Log.e("UpdatesNetworkDataSource", "Error fetching changelog from $url", e)
            null
        }
    }
}
