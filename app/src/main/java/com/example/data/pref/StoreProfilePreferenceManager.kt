package com.example.data.pref

import android.content.Context
import com.example.util.GstinComplianceHelper
import com.example.util.StoreGstMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class StoreIdentity(
    val storeName: String = "",
    val roleTitle: String = "Warehouse Supervisor",
    val location: String = "HardwareStock Central Depot",
    val supervisorName: String = "Shahazim",
    val storeGstMode: String = StoreGstMode.REGULAR.code,
    val storeGstin: String = "",
    val storeStateCode: String = ""
) {
    val gstModeEnum: StoreGstMode
        get() = StoreGstMode.fromCode(storeGstMode)

    val effectiveStateCode: String
        get() = GstinComplianceHelper.extractStateCode(storeGstin)
            ?: GstinComplianceHelper.extractStateCode(storeStateCode)
            ?: ""
}

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
        val rawMode = prefs.getString("store_gst_mode", StoreGstMode.REGULAR.code)
        val cleanMode = StoreGstMode.fromCode(rawMode).code
        val cleanGstin = GstinComplianceHelper.normalizeGstin(prefs.getString("store_gstin", ""))
        val rawState = GstinComplianceHelper.extractStateCode(prefs.getString("store_state_code", "")) ?: ""
        val cleanState = GstinComplianceHelper.extractStateCode(cleanGstin) ?: rawState
        return StoreIdentity(
            storeName = prefs.getString("store_name", "") ?: "",
            roleTitle = prefs.getString("supervisor_role", DEFAULT_ROLE_TITLE)
                ?.takeIf { it.isNotBlank() } ?: DEFAULT_ROLE_TITLE,
            location = prefs.getString("depot_location", DEFAULT_LOCATION)
                ?.takeIf { it.isNotBlank() } ?: DEFAULT_LOCATION,
            supervisorName = prefs.getString("supervisor_name", DEFAULT_SUPERVISOR_NAME)
                ?.takeIf { it.isNotBlank() } ?: DEFAULT_SUPERVISOR_NAME,
            storeGstMode = cleanMode,
            storeGstin = cleanGstin,
            storeStateCode = cleanState
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
        val cur = _storeIdentity.value

        prefs.edit()
            .putString("store_name", cleanStore)
            .putString("supervisor_name", cleanSupervisor)
            .putString("supervisor_role", cleanRole)
            .putString("depot_location", cleanLocation)
            .apply()

        _storeIdentity.value = cur.copy(
            storeName = cleanStore,
            roleTitle = cleanRole,
            location = cleanLocation,
            supervisorName = cleanSupervisor
        )
    }

    fun saveStoreGstConfig(
        gstMode: String,
        storeGstin: String,
        storeStateCode: String
    ) {
        val cleanMode = StoreGstMode.fromCode(gstMode)
        val cleanGstin = if (cleanMode == StoreGstMode.UNREGISTERED) {
            ""
        } else {
            GstinComplianceHelper.normalizeGstin(storeGstin)
        }
        val resolvedState = GstinComplianceHelper.extractStateCode(cleanGstin)
            ?: GstinComplianceHelper.extractStateCode(storeStateCode)
            ?: ""

        prefs.edit()
            .putString("store_gst_mode", cleanMode.code)
            .putString("store_gstin", cleanGstin)
            .putString("store_state_code", resolvedState)
            .apply()

        _storeIdentity.value = _storeIdentity.value.copy(
            storeGstMode = cleanMode.code,
            storeGstin = cleanGstin,
            storeStateCode = resolvedState
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
        location: String?,
        storeGstMode: String? = null,
        storeGstin: String? = null,
        storeStateCode: String? = null
    ) {
        val cur = _storeIdentity.value
        val nextStore = storeName?.trim() ?: cur.storeName
        val nextSupervisor = supervisorName?.trim()?.takeIf { it.isNotBlank() } ?: cur.supervisorName
        val nextRole = roleTitle?.trim()?.takeIf { it.isNotBlank() } ?: cur.roleTitle
        val nextLocation = location?.trim()?.takeIf { it.isNotBlank() } ?: cur.location
        val nextMode = storeGstMode?.trim()?.takeIf { it.isNotBlank() }?.let { StoreGstMode.fromCode(it).code }
            ?: cur.storeGstMode
        val nextGstin = if (storeGstin != null) {
            if (nextMode == StoreGstMode.UNREGISTERED.code) "" else GstinComplianceHelper.normalizeGstin(storeGstin)
        } else {
            cur.storeGstin
        }
        val nextState = GstinComplianceHelper.extractStateCode(nextGstin)
            ?: storeStateCode?.let { GstinComplianceHelper.extractStateCode(it) ?: "" }
            ?: cur.storeStateCode

        if (nextStore == cur.storeName &&
            nextSupervisor == cur.supervisorName &&
            nextRole == cur.roleTitle &&
            nextLocation == cur.location &&
            nextMode == cur.storeGstMode &&
            nextGstin == cur.storeGstin &&
            nextState == cur.storeStateCode
        ) {
            return
        }

        prefs.edit()
            .putString("store_name", nextStore)
            .putString("supervisor_name", nextSupervisor)
            .putString("supervisor_role", nextRole)
            .putString("depot_location", nextLocation)
            .putString("store_gst_mode", nextMode)
            .putString("store_gstin", nextGstin)
            .putString("store_state_code", nextState)
            .apply()

        _storeIdentity.value = StoreIdentity(
            storeName = nextStore,
            roleTitle = nextRole,
            location = nextLocation,
            supervisorName = nextSupervisor,
            storeGstMode = nextMode,
            storeGstin = nextGstin,
            storeStateCode = nextState
        )
    }
}
