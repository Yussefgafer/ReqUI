package com.android.requi.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.android.requi.model.CallRecording
import com.android.requi.model.RecordingRepository
import com.android.requi.parser.BcrTemplateParser
import com.android.requi.player.BcrAudioPlayer
import com.android.requi.preferences.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferencesManager(application)
    private val repository = RecordingRepository(application)
    val audioPlayer = BcrAudioPlayer(application)

    // Configuration States
    private val _folderUri = MutableStateFlow<String?>(prefs.folderUri)
    val folderUri: StateFlow<String?> = _folderUri

    private val _filenameTemplate = MutableStateFlow(prefs.filenameTemplate)
    val filenameTemplate: StateFlow<String> = _filenameTemplate

    private val _fileExtension = MutableStateFlow(prefs.fileExtension)
    val fileExtension: StateFlow<String> = _fileExtension

    private val _isOnboardingCompleted = MutableStateFlow(prefs.isOnboardingCompleted)
    val isOnboardingCompleted: StateFlow<Boolean> = _isOnboardingCompleted

    private val _accentColor = MutableStateFlow(prefs.accentColor)
    val accentColor: StateFlow<String> = _accentColor

    private val _amoledMode = MutableStateFlow(prefs.amoledMode)
    val amoledMode: StateFlow<Boolean> = _amoledMode

    // UI States
    private val _rawRecordings = MutableStateFlow<List<CallRecording>>(emptyList())
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _directionFilter = MutableStateFlow("all") // all, in, out
    val directionFilter: StateFlow<String> = _directionFilter

    private val _simFilter = MutableStateFlow<Int?>(null) // null (all), 1, 2
    val simFilter: StateFlow<Int?> = _simFilter

    private val _durationFilter = MutableStateFlow("all") // all, short, medium, long
    val durationFilter: StateFlow<String> = _durationFilter

    private val _dateFilter = MutableStateFlow("all") // all, today, yesterday, week, older, custom
    val dateFilter: StateFlow<String> = _dateFilter

    private val _customStartDate = MutableStateFlow<Long?>(null)
    val customStartDate: StateFlow<Long?> = _customStartDate

    private val _customEndDate = MutableStateFlow<Long?>(null)
    val customEndDate: StateFlow<Long?> = _customEndDate

    private val _contactFilter = MutableStateFlow<List<String>>(emptyList())
    val contactFilter: StateFlow<List<String>> = _contactFilter

    private val _selectedRecording = MutableStateFlow<CallRecording?>(null)
    val selectedRecording: StateFlow<CallRecording?> = _selectedRecording

    private val _recycledFiles = MutableStateFlow<List<com.android.requi.model.RecycledFile>>(emptyList())
    val recycledFiles: StateFlow<List<com.android.requi.model.RecycledFile>> = _recycledFiles

    private val _contactNames = MutableStateFlow<List<String>>(emptyList())
    val contactNames: StateFlow<List<String>> = _contactNames

    private var contactsMap = emptyMap<String, String>()

    // Combine filters and search query with raw recordings
    @Suppress("UNCHECKED_CAST")
    val recordings: StateFlow<List<CallRecording>> = combine(
        _rawRecordings,
        _searchQuery,
        _directionFilter,
        _simFilter,
        _durationFilter,
        _dateFilter,
        _contactFilter,
        _customStartDate,
        _customEndDate
    ) { flows ->
        val raw = flows[0] as List<CallRecording>
        val query = flows[1] as String
        val direction = flows[2] as String
        val sim = flows[3] as Int?
        val durationRange = flows[4] as String
        val dateRange = flows[5] as String
        val contactVal = flows[6] as List<String>
        val customStart = flows[7] as Long?
        val customEnd = flows[8] as Long?

        var list = raw

        // Filter by specific contacts (match ANY selected contact)
        if (contactVal.isNotEmpty()) {
            list = list.filter { recording ->
                contactVal.any { cf ->
                    val cfLower = cf.lowercase().trim()
                    recording.resolvedName.lowercase().contains(cfLower) ||
                            (recording.phoneNumber ?: "").lowercase().contains(cfLower)
                }
            }
        }

        // Filter by search query
        if (query.isNotBlank()) {
            val q = query.lowercase().trim()
            list = list.filter {
                it.resolvedName.lowercase().contains(q) ||
                        (it.phoneNumber ?: "").lowercase().contains(q) ||
                        (it.date ?: "").lowercase().contains(q)
            }
        }

        // Filter by direction
        if (direction != "all") {
            list = list.filter { it.direction?.lowercase() == direction }
        }


        if (sim != null) {
            list = list.filter { it.simSlot == sim }
        }

        // Filter by duration range
        if (durationRange != "all") {
            list = when (durationRange) {
                "short" -> list.filter { it.durationMs < 60000L } // < 1 min
                "medium" -> list.filter { it.durationMs in 60000L..300000L } // 1 - 5 mins
                "long" -> list.filter { it.durationMs > 300000L } // > 5 mins
                else -> list
            }
        }
        if (dateRange != "all") {
            if (dateRange == "custom") {
                if (customStart != null) {
                    list = list.filter { it.lastModified >= customStart }
                }
                if (customEnd != null) {
                    list = list.filter { it.lastModified <= customEnd }
                }
            } else {
                val now = System.currentTimeMillis()
                val dayMs = 24 * 60 * 60 * 1000L
                list = when (dateRange) {
                    "today" -> list.filter { now - it.lastModified < dayMs }
                    "yesterday" -> list.filter {
                        val age = now - it.lastModified
                        age in dayMs..(dayMs * 2)
                    }
                    "week" -> list.filter { now - it.lastModified < dayMs * 7 }
                    "older" -> list.filter { now - it.lastModified >= dayMs * 7 }
                    else -> list
                }
            }
        }

        list
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Player State Flows delegated from audioPlayer
    val isPlaying: StateFlow<Boolean> = audioPlayer.isPlaying
    val currentPosition: StateFlow<Long> = audioPlayer.currentPosition
    val duration: StateFlow<Long> = audioPlayer.duration
    val playbackSpeed: StateFlow<Float> = audioPlayer.playbackSpeed

    fun setDurationFilter(filter: String) {
        _durationFilter.value = filter
    }

    fun setDateFilter(filter: String) {
        _dateFilter.value = filter
        if (filter != "custom") {
            _customStartDate.value = null
            _customEndDate.value = null
        }
    }

    fun setCustomDateRange(start: Long?, end: Long?) {
        _customStartDate.value = start
        _customEndDate.value = end
        _dateFilter.value = if (start != null || end != null) "custom" else "all"
    }

    fun addContactFilter(name: String) {
        if (name.isNotBlank() && !_contactFilter.value.contains(name)) {
            _contactFilter.value = _contactFilter.value + name
        }
    }

    fun removeContactFilter(name: String) {
        _contactFilter.value = _contactFilter.value - name
    }

    fun clearContactFilters() {
        _contactFilter.value = emptyList()
    }

    fun loadContacts() {
        viewModelScope.launch(Dispatchers.IO) {
            val map = mutableMapOf<String, String>()
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    getApplication(),
                    android.Manifest.permission.READ_CONTACTS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                val uri = android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI
                val projection = arrayOf(
                    android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER,
                    android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                )
                try {
                    getApplication<Application>().contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                        val numberIndex = cursor.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER)
                        val nameIndex = cursor.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                        while (cursor.moveToNext()) {
                            val number = cursor.getString(numberIndex)
                            val name = cursor.getString(nameIndex)
                            if (number != null && name != null) {
                                val normalized = android.telephony.PhoneNumberUtils.normalizeNumber(number)
                                if (normalized.isNotEmpty()) {
                                    map[normalized] = name
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            contactsMap = map
            _contactNames.value = map.values.distinct().sorted()
            // Trigger list refresh to apply contact names to existing raw recordings
            if (_rawRecordings.value.isNotEmpty()) {
                val updatedList = _rawRecordings.value.map { recording ->
                    val phone = recording.phoneNumber
                    if (phone != null) {
                        val normalizedPhone = android.telephony.PhoneNumberUtils.normalizeNumber(phone)
                        val systemName = contactsMap[normalizedPhone] ?: contactsMap[phone]
                        if (systemName != null) {
                            recording.copy(contactName = systemName)
                        } else {
                            recording
                        }
                    } else {
                        recording
                    }
                }
                _rawRecordings.value = updatedList
            }
            loadRecycledFiles()
        }
    }

    fun loadRecycledFiles() {
        viewModelScope.launch {
            val template = _filenameTemplate.value
            _recycledFiles.value = repository.getRecycledRecordings(template, contactsMap)
        }
    }

    init {
        loadContacts()
        loadRecycledFiles()
        if (prefs.isOnboardingCompleted && !prefs.folderUri.isNullOrEmpty()) {
            loadRecordings()
        }
    }

    fun loadRecordings() {
        val folder = _folderUri.value ?: return
        val template = _filenameTemplate.value
        val ext = _fileExtension.value

        viewModelScope.launch {
            _isLoading.value = true
            try {
                var list = repository.getRecordings(folder, template, ext)
                if (contactsMap.isNotEmpty()) {
                    list = list.map { recording ->
                        val phone = recording.phoneNumber
                        if (phone != null) {
                            val normalizedPhone = android.telephony.PhoneNumberUtils.normalizeNumber(phone)
                            val systemName = contactsMap[normalizedPhone] ?: contactsMap[phone]
                            if (systemName != null) {
                                recording.copy(contactName = systemName)
                            } else {
                                recording
                            }
                        } else {
                            recording
                        }
                    }
                }
                _rawRecordings.value = list
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun saveSettings(folder: String?, template: String, extension: String, accent: String, amoled: Boolean) {
        prefs.folderUri = folder
        prefs.filenameTemplate = template
        prefs.fileExtension = extension
        prefs.accentColor = accent
        prefs.amoledMode = amoled

        _folderUri.value = folder
        _filenameTemplate.value = template
        _fileExtension.value = extension
        _accentColor.value = accent
        _amoledMode.value = amoled

        loadRecordings()
    }

    fun completeOnboarding(folder: String, template: String, extension: String) {
        saveSettings(folder, template, extension, PreferencesManager.DEFAULT_ACCENT_COLOR, false)
        prefs.isOnboardingCompleted = true
        _isOnboardingCompleted.value = true
    }

    fun resetOnboarding() {
        prefs.clear()
        _folderUri.value = null
        _filenameTemplate.value = PreferencesManager.DEFAULT_TEMPLATE
        _fileExtension.value = PreferencesManager.DEFAULT_EXTENSION
        _accentColor.value = PreferencesManager.DEFAULT_ACCENT_COLOR
        _amoledMode.value = false
        _isOnboardingCompleted.value = false
        _rawRecordings.value = emptyList()
        _selectedRecording.value = null
        audioPlayer.stop()
    }

    fun selectRecording(recording: CallRecording?) {
        _selectedRecording.value = recording
        if (recording != null) {
            audioPlayer.play(
                recording.uri,
                recording.resolvedName,
                recording.resolvedSubtext ?: ""
            )
        } else {
            audioPlayer.stop()
        }
    }

    fun togglePlayPause() {
        if (isPlaying.value) {
            audioPlayer.pause()
        } else {
            _selectedRecording.value?.let {
                audioPlayer.play(
                    it.uri,
                    it.resolvedName,
                    it.resolvedSubtext ?: ""
                )
            }
        }
    }

    fun seekTo(position: Long) {
        audioPlayer.seekTo(position)
    }

    fun setPlaybackSpeed(speed: Float) {
        audioPlayer.setPlaybackSpeed(speed)
    }

    fun skipForward() {
        val newPos = (currentPosition.value + 10000).coerceAtMost(duration.value)
        audioPlayer.seekTo(newPos)
    }

    fun skipBackward() {
        val newPos = (currentPosition.value - 10000).coerceAtLeast(0L)
        audioPlayer.seekTo(newPos)
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setDirectionFilter(direction: String) {
        _directionFilter.value = direction
    }

    fun setSimFilter(sim: Int?) {
        _simFilter.value = sim
    }

    fun deleteRecording(recording: CallRecording) {
        val folder = _folderUri.value ?: return
        viewModelScope.launch {
            val success = repository.deleteRecording(folder, recording)
            if (success) {
                if (_selectedRecording.value?.uri == recording.uri) {
                    selectRecording(null)
                }
                loadRecordings()
                loadRecycledFiles()
            }
        }
    }

    fun restoreRecycledFile(fileName: String) {
        val folder = _folderUri.value ?: return
        viewModelScope.launch {
            val success = repository.restoreRecording(folder, fileName)
            if (success) {
                loadRecordings()
                loadRecycledFiles()
            }
        }
    }

    fun deletePermanently(fileName: String) {
        viewModelScope.launch {
            val success = repository.deletePermanently(fileName)
            if (success) {
                loadRecycledFiles()
            }
        }
    }

    fun emptyRecycleBin() {
        viewModelScope.launch {
            val success = repository.emptyRecycleBin()
            if (success) {
                loadRecycledFiles()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioPlayer.release()
    }
}
