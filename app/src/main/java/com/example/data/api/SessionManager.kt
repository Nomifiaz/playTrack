package com.example.data.api

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("paytrack_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_TOKEN = "jwt_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_CURRENCY = "currency"
        private const val KEY_ONBOARDED = "onboarded"
    }

    var token: String?
        get() = prefs.getString(KEY_TOKEN, null)
        set(value) {
            prefs.edit().putString(KEY_TOKEN, value).apply()
        }

    var userId: String?
        get() = prefs.getString(KEY_USER_ID, null)
        set(value) {
            prefs.edit().putString(KEY_USER_ID, value).apply()
        }

    var userName: String?
        get() = prefs.getString(KEY_USER_NAME, null)
        set(value) {
            prefs.edit().putString(KEY_USER_NAME, value).apply()
        }

    var userEmail: String?
        get() = prefs.getString(KEY_USER_EMAIL, null)
        set(value) {
            prefs.edit().putString(KEY_USER_EMAIL, value).apply()
        }

    var currency: String?
        get() = prefs.getString(KEY_CURRENCY, null)
        set(value) {
            prefs.edit().putString(KEY_CURRENCY, value).apply()
        }

    var isOnboarded: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_ONBOARDED, value).apply()
        }

    fun logout() {
        prefs.edit().clear().apply()
    }
}
