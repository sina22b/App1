package com.example

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

// Note data model
data class Note(
    val id: String,
    val content: String,
    val timestamp: Long
) {
    fun getFormattedDate(): String {
        val sdf = SimpleDateFormat("EEEE, MMMM d, yyyy 'at' h:mm a", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}

// AndroidViewModel for offline local notes persistence using standard SharedPreferences
class NotesViewModel(application: Application) : AndroidViewModel(application) {
    private val sharedPrefs: SharedPreferences =
        application.getSharedPreferences("voice_friendly_notes_prefs", Context.MODE_PRIVATE)

    private val _notes = MutableStateFlow<List<Note>>(emptyList())
    val notes: StateFlow<List<Note>> = _notes.asStateFlow()

    // Screen reader polite live announcement state
    private val _announcement = MutableStateFlow<String?>(null)
    val announcement: StateFlow<String?> = _announcement.asStateFlow()

    init {
        loadNotes()
    }

    private fun loadNotes() {
        try {
            val jsonStr = sharedPrefs.getString("notes_json", "[]") ?: "[]"
            val list = mutableListOf<Note>()
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    Note(
                        id = obj.getString("id"),
                        content = obj.getString("content"),
                        timestamp = obj.getLong("timestamp")
                    )
                )
            }
            _notes.value = list.sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            Log.e("NotesViewModel", "Failed to load notes", e)
            _notes.value = emptyList()
        }
    }

    fun saveNote(content: String, onSuccessMessage: String, onEmptyMessage: String) {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) {
            triggerAnnouncement(onEmptyMessage)
            return
        }

        val newNote = Note(
            id = UUID.randomUUID().toString(),
            content = trimmed,
            timestamp = System.currentTimeMillis()
        )

        val updatedNotes = _notes.value.toMutableList().apply {
            add(0, newNote) // Place at index 0 (newest first)
        }

        persistNotes(updatedNotes)
        
        // Trigger semantic polite announcement
        val successText = String.format(onSuccessMessage, trimmed)
        triggerAnnouncement(successText)
    }

    fun deleteNote(note: Note, onDeleteMessage: String) {
        val updatedNotes = _notes.value.filter { it.id != note.id }
        persistNotes(updatedNotes)
        
        // Trigger semantic polite announcement
        val deleteText = String.format(onDeleteMessage, note.content)
        triggerAnnouncement(deleteText)
    }

    fun clearAllNotes(onClearedMessage: String) {
        persistNotes(emptyList())
        triggerAnnouncement(onClearedMessage)
    }

    private fun persistNotes(newNotesList: List<Note>) {
        try {
            val sortedList = newNotesList.sortedByDescending { it.timestamp }
            val array = JSONArray()
            for (note in sortedList) {
                val obj = JSONObject()
                obj.put("id", note.id)
                obj.put("content", note.content)
                obj.put("timestamp", note.timestamp)
                array.put(obj)
            }
            val jsonStr = array.toString()
            sharedPrefs.edit().putString("notes_json", jsonStr).apply()
            _notes.value = sortedList
        } catch (e: Exception) {
            Log.e("NotesViewModel", "Failed to persist notes", e)
        }
    }

    private fun triggerAnnouncement(message: String) {
        _announcement.value = ""
        _announcement.value = message
    }
}

// Main Activity entrypoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HighContrastTheme {
                NotesApp()
            }
        }
    }
}

// Custom AA WCAG Compliant High Contrast Dark Theme
@Composable
fun HighContrastTheme(content: @Composable () -> Unit) {
    val highContrastDarkColors = darkColorScheme(
        primary = Color(0xFF00E5FF),          // High readability bold cyan highlight
        onPrimary = Color(0xFF000000),        // Absolute black text for excellent contrast
        primaryContainer = Color(0xFF00363A), // Pine dark teal
        onPrimaryContainer = Color(0xFFE0F7FA),
        secondary = Color(0xFF80DEEA),        // High-contrast ice cyan
        onSecondary = Color(0xFF000000),
        background = Color(0xFF070B16),       // Ultra-dark background
        onBackground = Color(0xFFFFFFFF),     // Brightest white text
        surface = Color(0xFF131D31),          // High contrast slate surfaces for card containers
        onSurface = Color(0xFFFFFFFF),        // Solid white surface readable text
        surfaceVariant = Color(0xFF1E2D4A),   // Slightly brighter accent surfaces
        onSurfaceVariant = Color(0xFFEDF2F7), // Light gray descriptive text exceeding 7:1 ratio
        outline = Color(0xFF00E5FF),          // Cyan boundary outline
        error = Color(0xFFFF5252),            // Safe high contrast action warning pink/red
        onError = Color(0xFF000000),
        errorContainer = Color(0xFF451A1A),
        onErrorContainer = Color(0xFFFFDAD4)
    )

    MaterialTheme(
        colorScheme = highContrastDarkColors,
        content = content
    )
}

// Complete accessible Voice Friendly Notes user interface
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesApp(viewModel: NotesViewModel = viewModel()) {
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val rawAnnouncement by viewModel.announcement.collectAsStateWithLifecycle()
    
    // Manage local announcement state for Jetpack Compose polite live region
    var localAnnouncement by remember { mutableStateOf("") }
    LaunchedEffect(rawAnnouncement) {
        if (!rawAnnouncement.isNullOrEmpty()) {
            localAnnouncement = rawAnnouncement!!
        }
    }
    
    var noteInputText by remember { mutableStateOf("") }
    val context = LocalContext.current
    var showClearConfirmationDialog by remember { mutableStateOf(false) }

    // Character length limits
    val charLimit = 350
    
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.notes_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.semantics {
                            contentDescription = context.getString(R.string.notes_title)
                        }
                    )
                },
                actions = {
                    // Prompt clear all note action
                    Button(
                        onClick = { showClearConfirmationDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("clear_all_button")
                            .semantics {
                                contentDescription = context.getString(R.string.clear_all_desc)
                            }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.clear_all_button),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { innerPadding ->
        
        // Semantic liveRegion helper node to broadcast verbal updates to TalkBack users
        Box(
            modifier = Modifier
                .size(1.dp)
                .semantics {
                    liveRegion = LiveRegionMode.Polite
                }
        ) {
            Text(text = localAnnouncement)
        }
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // Note Editor Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp)
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.note_input_helper_text),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    )
                    
                    OutlinedTextField(
                        value = noteInputText,
                        onValueChange = {
                            if (it.length <= charLimit) {
                                noteInputText = it
                            }
                        },
                        label = { Text(text = stringResource(R.string.note_input_label)) },
                        placeholder = { Text(text = stringResource(R.string.note_input_placeholder)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("note_input_field")
                            .semantics {
                                contentDescription = context.getString(R.string.note_input_desc)
                            },
                        supportingText = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${noteInputText.length}/$charLimit",
                                    color = if (noteInputText.length >= charLimit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = "Supported Offline",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    )
                    
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    // Large tactile, easily targetable Save Note action
                    Button(
                        onClick = {
                            val msgSuccess = context.getString(R.string.note_saved_announcement)
                            val msgEmpty = context.getString(R.string.input_empty_announcement)
                            viewModel.saveNote(
                                content = noteInputText,
                                onSuccessMessage = msgSuccess,
                                onEmptyMessage = msgEmpty
                            )
                            if (noteInputText.trim().isNotEmpty()) {
                                noteInputText = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("save_note_button")
                            .semantics {
                                contentDescription = context.getString(R.string.save_note_desc)
                            }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Done,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.save_note_button),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Header for notes list
            Text(
                text = "Saved Notes (${notes.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .semantics {
                        contentDescription = "Saved Notes section, listing ${notes.size} offline entries saved in local memory."
                    }
            )
            
            if (notes.isEmpty()) {
                // Empty notebook friendly state instructioncard
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.no_notes_title),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.no_notes_subtitle),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )
                    }
                }
            } else {
                // Interactive Scrollable List
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(
                        items = notes,
                        key = { it.id }
                    ) { note ->
                        NoteItemRow(
                            note = note,
                            onDelete = {
                                val deleteMsg = context.getString(R.string.note_deleted_announcement)
                                viewModel.deleteNote(note, deleteMsg)
                            },
                            context = context
                        )
                    }
                }
            }
        }
    }
    
    // High accessibility Dialog configuration
    if (showClearConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmationDialog = false },
            title = {
                Text(
                    text = "Clear All Notes?",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to permanently delete all saved notes from your device? This offline action is immediate and cannot be recovered.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clearedMsg = context.getString(R.string.all_notes_cleared_announcement)
                        viewModel.clearAllNotes(clearedMsg)
                        showClearConfirmationDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("confirm_clear_dialog_button")
                ) {
                    Text(text = "Delete All")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showClearConfirmationDialog = false },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.testTag("cancel_clear_dialog_button")
                ) {
                    Text(text = "Cancel")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

// Single list note entry layout
@Composable
fun NoteItemRow(
    note: Note,
    onDelete: () -> Unit,
    context: Context
) {
    // TalkBack focused single announcement formula
    val noteSpeechText = context.getString(
        R.string.note_item_description_prefix,
        note.getFormattedDate(),
        note.content
    )
    
    val deleteSpeechText = context.getString(
        R.string.delete_note_labelled,
        if (note.content.length > 25) note.content.take(22) + "..." else note.content
    )
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("note_item_${note.id}")
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // High visibility, group-merged note details
        Column(
            modifier = Modifier
                .weight(1f)
                .semantics(mergeDescendants = true) {
                    contentDescription = noteSpeechText
                }
                .padding(end = 8.dp)
        ) {
            Text(
                text = note.content,
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 21.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = note.getFormattedDate(),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }
        
        // Physical Action button next to item, matching exact finger touch rules and labeled Voice triggers
        Button(
            onClick = onDelete,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            ),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
            modifier = Modifier
                .testTag("delete_note_button_${note.id}")
                .height(48.dp) // Enforces perfect finger interactive height threshold
                .semantics {
                    contentDescription = deleteSpeechText
                }
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Delete",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

