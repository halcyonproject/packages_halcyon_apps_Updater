/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.hlcyn.updater.deviceinfo

import android.os.Build
import android.os.SystemProperties
import com.android.settingslib.DeviceInfoUtils as SettingsLibDeviceInfoUtils

object DeviceInfoUtils : SettingsLibDeviceInfoUtils() {

    private const val PROP_AB_DEVICE = "ro.build.ab_update"
    private const val PROP_ALLOW_MAJOR_UPGRADES = "halcyon.updater.allow_major_upgrades"
    private const val PROP_BUILD_DATE = "ro.build.date.utc"
    private const val PROP_BUILD_VERSION = "ro.halcyon.build.version"
    private const val PROP_BUILD_VERSION_INCREMENTAL = "ro.build.version.incremental"
    private const val PROP_DEVICE = "ro.halcyon.device"
    private const val PROP_NEXT_DEVICE = "ro.updater.next_device"
    private const val PROP_RELEASE_TYPE = "ro.halcyon.build.type"
    private const val PROP_HALCYON_VERSION = "ro.halcyon.version"
    private const val PROP_BUILD_VARIANT = "ro.halcyon.build.variant"
    private const val PROP_HALCYON_BUILD_DATE = "ro.halcyon.build.date"
    private const val PROP_UPDATER_ALLOW_DOWNGRADING = "halcyon.updater.allow_downgrading"
    private const val PROP_UPDATER_URI = "halcyon.updater.uri"
    private const val PROP_UPDATE_RECOVERY = "persist.vendor.recovery_update"

    // Read-only
    val androidVersion: String = Build.VERSION.RELEASE

    val buildSecurityPatch: String = Build.VERSION.SECURITY_PATCH

    val sdkLevel: Int = Build.VERSION.SDK_INT

    @JvmStatic
    val buildDateTimestamp: Long = SystemProperties.getLong(PROP_BUILD_DATE, 0)

    @JvmStatic
    val buildVersion: String = SystemProperties.get(PROP_BUILD_VERSION, "")

    @JvmStatic
    val buildVersionIncremental: String = SystemProperties.get(PROP_BUILD_VERSION_INCREMENTAL, "")

    @JvmStatic
    val device: String = SystemProperties.get(PROP_NEXT_DEVICE, SystemProperties.get(PROP_DEVICE))

    @JvmStatic
    val halcyonVersion: String
        get() = SystemProperties.get(PROP_HALCYON_VERSION, "").ifEmpty {
            SystemProperties.get(PROP_BUILD_VERSION, "")
        }

    @JvmStatic
    val variant: String
        get() {
            val prop = SystemProperties.get(PROP_BUILD_VARIANT, "")
            if (prop.isNotEmpty()) return prop
            val ver = buildVersion
            return when {
                ver.contains("GMS", ignoreCase = true) -> "GMS"
                ver.contains("VANILLA", ignoreCase = true) -> "VANILLA"
                else -> ""
            }
        }

    @JvmStatic
    fun resolveBuildDate(updateName: String? = null): String {
        if (!updateName.isNullOrEmpty()) {
            val match = Regex("""\d{8}-\d{4}""").find(updateName)
            if (match != null) return match.value
        }

        val prop = SystemProperties.get(PROP_HALCYON_BUILD_DATE, "").trim()
        if (prop.isNotEmpty()) return prop

        val verMatch = Regex("""\d{8}-\d{4}""").find(buildVersion)
        if (verMatch != null) return verMatch.value

        val numMatch = Regex("""\d{8}-\d{4}""").find(SystemProperties.get("ro.halcyon.build.number", ""))
        if (numMatch != null) return numMatch.value

        return ""
    }

    @JvmStatic
    val isABDevice: Boolean = SystemProperties.getBoolean(PROP_AB_DEVICE, false)

    @JvmStatic
    val releaseType: String = SystemProperties.get(PROP_RELEASE_TYPE)

    // Mutable at runtime
    @JvmStatic
    val isDowngradingAllowed: Boolean
        get() = SystemProperties.getBoolean(PROP_UPDATER_ALLOW_DOWNGRADING, false)

    @JvmStatic
    val isMajorUpdateAllowed: Boolean
        get() = SystemProperties.getBoolean(PROP_ALLOW_MAJOR_UPGRADES, false)

    @JvmStatic
    var isRecoveryUpdateEnabled: Boolean
        get() = SystemProperties.getBoolean(PROP_UPDATE_RECOVERY, false)
        set(value) = SystemProperties.set(PROP_UPDATE_RECOVERY, value.toString())

    @JvmStatic
    val updaterUri: String
        get() = SystemProperties.get(PROP_UPDATER_URI, "")
}
