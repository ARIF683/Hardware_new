package com.example.data.pref

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class StoreIdentity(
    val storeName: String = "",
    val roleTitle: String = "Warehouse Supervisor",
    val location: String = "HardwareStock Central Depot",
    val supervisorName: String = "Shahazim"
)

class StoreProfilePreferenceManager(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)

    companion object {
        const val DEFAULT_ROLE_TITLE = "Warehouse Supervisor"
        const val DEFAULT_LOCATION = "HardwareStock Central Depot"
        const val DEFAULT_SUPERVISOR_NAME = "Shahazim"

        @Volatile
        private var isInitialized = false
        private val _storeIdentity = MutableStateFlow(StoreIdentity())
        val storeIdentity: StateFlow<StoreIdentity> = _storeIdentity.asStateFlow()
    }

    init {
        synchronized(StoreProfilePreferenceManager::class.java) {
            if (!isInitialized) {
                _storeIdentity.value = loadStoreIdentity()
                isInitialized = true
            }
        }
    }

    val currentStoreIdentity: StateFlow<StoreIdentity> = Companion.storeIdentity

    fun reloadFromPrefs(): StoreIdentity {
        val loaded = loadStoreIdentity()
        _storeIdentity.value = loaded
        return loaded
    }

    private fun loadStoreIdentity(): StoreIdentity {
        return StoreIdentity(
            storeName = prefs.getString("store_name", "") ?: "",
            roleTitle = prefs.getString("supervisor_role", DEFAULT_ROLE_TITLE)
                ?.takeIf { it.isNotBlank() } ?: DEFAULT_ROLE_TITLE,
            location = prefs.getString("depot_location", DEFAULT_LOCATION)
                ?.takeIf { it.isNotBlank() } ?: DEFAULT_LOCATION,
            supervisorName = prefs.getString("supervisor_name", DEFAULT_SUPERVISOR_NAME)
                ?.takeIf { it.isNotBlank() } ?: DEFAULT_SUPERVISOR_NAME
        )
    }

    fun saveStoreIdentity(
        storeName: String,
        supervisorName: String,
        roleTitle: String,
        location: String
    ) {
        val cleanStore = storeName.trim()
        val cleanSupervisor = supervisorName.trim().ifBlank { DEFAULT_SUPERVISOR_NAME }
        val cleanRole = roleTitle.trim().ifBlank { DEFAULT_ROLE_TITLE }
        val cleanLocation = location.trim().ifBlank { DEFAULT_LOCATION }

        prefs.edit()
            .putString("store_name", cleanStore)
            .putString("supervisor_name", cleanSupervisor)
            .putString("supervisor_role", cleanRole)
            .putString("depot_location", cleanLocation)
            .apply()

        _storeIdentity.value = StoreIdentity(
            storeName = cleanStore,
            roleTitle = cleanRole,
            location = cleanLocation,
            supervisorName = cleanSupervisor
        )
    }

    /**
     * Merges incoming remote store identity fields.
     * Null values are ignored (preserves local state); blank role/location/name retain fallback defaults.
     */
    fun applyRemoteStoreIdentity(
        storeName: String?,
        supervisorName: String?,
        roleTitle: String?,
        location: String?
    ) {
        val cur = _storeIdentity.value
        val nextStore = storeName?.trim() ?: cur.storeName
        val nextSupervisor = supervisorName?.trim()?.takeIf { it.isNotBlank() } ?: cur.supervisorName
        val nextRole = roleTitle?.trim()?.takeIf { it.isNotBlank() } ?: cur.roleTitle
        val nextLocation = location?.trim()?.takeIf { it.isNotBlank() } ?: cur.location

        if (nextStore == cur.storeName &&
            nextSupervisor == cur.supervisorName &&
            nextRole == cur.roleTitle &&
            nextLocation == cur.location
        ) {
            return
        }

        prefs.edit()
            .putString("store_name", nextStore)
            .putString("supervisor_name", nextSupervisor)
            .putString("supervisor_role", nextRole)
            .putString("depot_location", nextLocation)
            .apply()

        _storeIdentity.value = StoreIdentity(
            storeName = nextStore,
            roleTitle = nextRole,
            location = nextLocation,
            supervisorName = nextSupervisor
        )
    }
}
