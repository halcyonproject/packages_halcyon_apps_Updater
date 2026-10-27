/*
 * SPDX-FileCopyrightText: 2026 The Halcyon Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.hlcyn.updater.util

import android.content.Context
import android.os.PersistableBundle
import android.os.SystemUpdateManager
import android.provider.Settings
import android.util.Log

object SystemUpdateStatusHelper {
    const val KEY_HAS_UPDATE = "halcyon_updater_has_update"
    const val KEY_UPDATE_VERSION = "halcyon_updater_update_version"
    const val KEY_LAST_CHECK = "halcyon_updater_last_check"

    private const val TAG = "SystemUpdateStatus"

    fun updateStatus(
        context: Context,
        hasUpdate: Boolean,
        updateVersion: String? = null,
        lastCheckedTimestamp: Long? = null,
    ) {
        try {
            val resolver = context.contentResolver
            Settings.Secure.putInt(resolver, KEY_HAS_UPDATE, if (hasUpdate) 1 else 0)
            if (updateVersion != null) {
                Settings.Secure.putString(resolver, KEY_UPDATE_VERSION, updateVersion)
            } else if (!hasUpdate) {
                Settings.Secure.putString(resolver, KEY_UPDATE_VERSION, "")
            }
            if (lastCheckedTimestamp != null && lastCheckedTimestamp > 0) {
                Settings.Secure.putLong(resolver, KEY_LAST_CHECK, lastCheckedTimestamp)
            }

            val sum = context.getSystemService(SystemUpdateManager::class.java)
            if (sum != null) {
                val status = if (hasUpdate) {
                    SystemUpdateManager.STATUS_WAITING_DOWNLOAD
                } else {
                    SystemUpdateManager.STATUS_IDLE
                }
                val bundle = PersistableBundle().apply {
                    putInt(SystemUpdateManager.KEY_STATUS, status)
                    putString(SystemUpdateManager.KEY_TITLE, updateVersion ?: "")
                }
                sum.updateSystemUpdateInfo(bundle)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update system update info", e)
        }
    }
}
