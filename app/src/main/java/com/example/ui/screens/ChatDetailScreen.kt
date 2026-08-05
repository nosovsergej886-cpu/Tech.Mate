package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.TechMateRepository
import com.example.model.GuideData
import com.example.model.KnowledgeBaseEntry
import com.example.model.Message
import com.example.service.AiService
import com.example.ui.theme.AiBlueDark
import com.example.ui.theme.AiBlueLight
import com.example.ui.theme.TechGreenDark
import com.example.ui.theme.TechGreenLight
import com.example.ui.theme.WarningOrange
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatDetailScreen(
    chatId: String,
    repository: TechMateRepository,
    aiService: AiService,
    onBack: () -> Unit,
    onSaveToKbRequest: (GuideData) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val chats by repository.chats.collectAsState()
    val messagesMap by repository.messagesMap.collectAsState()
    val currentChat = remember(chats, chatId) { chats.find { it.id == chatId } }
    val messages = remember(messagesMap, chatId) { messagesMap[chatId] ?: emptyList() }

    var inputText by remember { mutableStateOf("") }
    var selectedImageBase64 by remember { mutableStateOf<String?>(null) }
    var isThinking by remember { mutableStateOf(false) }

    var fullscreenImageUrl by remember { mutableStateOf<String?>(null) }
    var showAttachmentSheet by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    fun sendMessage() {
        val textToSend = inputText.trim()
        val imageToSend = selectedImageBase64
        if (textToSend.isBlank() && imageToSend == null) return

        inputText = ""
        selectedImageBase64 = null

        // User message
        val userMsg = Message(
            chatId = chatId,
            role = "user",
            type = if (imageToSend != null) "image" else "text",
            text = textToSend.ifBlank { "Component picture attached" },
            imageUrl = imageToSend
        )
        repository.addMessage(userMsg)

        // Trigger AI thinking
        isThinking = true
        coroutineScope.launch {
            val history = repository.messagesMap.value[chatId] ?: emptyList()
            val (aiText, guideData) = aiService.sendMessage(
                userText = userMsg.text ?: "",
                imageBase64 = imageToSend,
                contextHistory = history
            )

            val aiMsg = Message(
                chatId = chatId,
                role = "ai",
                type = if (guideData != null) "guide" else "text",
                text = aiText,
                guideData = guideData
            )

            isThinking = false
            repository.addMessage(aiMsg)

            // Auto-update chat title if title is default
            if (currentChat?.title == "New Repair Inquiry" || currentChat?.title == "New Repair Diagnosis") {
                guideData?.let {
                    repository.updateChatTitle(chatId, "${it.device} — ${it.problem}")
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = currentChat?.title ?: "Repair Session",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = "Sensei AI • 15 Yrs Technician Master",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { repository.togglePinChat(chatId) }) {
                        Icon(
                            imageVector = if (currentChat?.isPinned == true) Icons.Default.PushPin else Icons.Default.PinEnd,
                            contentDescription = "Pin Chat"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Messages List (reversed)
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = listState,
                reverseLayout = true,
                contentPadding = PaddingValues(16.dp)
            ) {
                if (isThinking) {
                    item {
                        TypingIndicatorBubble()
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                items(messages.reversed(), key = { it.id }) { msg ->
                    MessageBubbleItem(
                        message = msg,
                        onImageClick = { fullscreenImageUrl = it },
                        onSaveToKb = { guide -> onSaveToKbRequest(guide) },
                        onFeedback = { solved ->
                            val text = if (solved) "Yes, problem is solved! Offer to save to Knowledge Base." else "No, need more diagnostic tests."
                            inputText = text
                            sendMessage()
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            // Quick Replies Row
            QuickRepliesRow(onSelectChip = { chipText ->
                inputText = if (inputText.isBlank()) chipText else "$inputText $chipText"
            })

            // Attachment Thumbnail Preview
            selectedImageBase64?.let { base64 ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(8.dp)
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Component Image Attached", style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.weight(1f))
                            IconButton(onClick = { selectedImageBase64 = null }) {
                                Icon(Icons.Default.Close, contentDescription = "Remove")
                            }
                        }
                    }
                }
            }

            // Input Bar
            Surface(
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .navigationBarsPadding()
                        .imePadding(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { showAttachmentSheet = true }) {
                        Icon(
                            Icons.Default.AttachFile,
                            contentDescription = "Attach photo",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    TextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("Describe the problem...", fontSize = 14.sp) },
                        modifier = Modifier
                            .weight(1f)
                            .clip(CircleShape),
                        shape = CircleShape,
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        singleLine = false,
                        maxLines = 4
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    if (inputText.isNotBlank() || selectedImageBase64 != null) {
                        FloatingActionButton(
                            onClick = { sendMessage() },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                        }
                    }
                }
            }
        }

        // Fullscreen Image Dialog Viewer
        fullscreenImageUrl?.let { url ->
            Dialog(onDismissRequest = { fullscreenImageUrl = null }) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.9f))
                        .clickable { fullscreenImageUrl = null },
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = url,
                        contentDescription = "Full Image View",
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                            .padding(16.dp)
                    )
                    IconButton(
                        onClick = { fullscreenImageUrl = null },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
            }
        }

        // Attachment Dialog
        if (showAttachmentSheet) {
            AlertDialog(
                onDismissRequest = { showAttachmentSheet = false },
                title = { Text("Attach Component Photo") },
                text = { Text("Select sample component macro shot for AI Sensei analysis.") },
                confirmButton = {
                    TextButton(onClick = {
                        // Sample component photo base64 simulation
                        selectedImageBase64 = "https://images.unsplash.com/photo-1518770660439-4636190af475?w=600&auto=format&fit=crop&q=60"
                        showAttachmentSheet = false
                    }) {
                        Text("Attach Sample PCB Photo")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAttachmentSheet = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun QuickRepliesRow(onSelectChip: (String) -> Unit) {
    val chips = listOf("Moisture", "Impact", "Reboot", "Not Charging", "No Power", "No Service")
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(chips) { chip ->
            AssistChip(
                onClick = { onSelectChip(chip) },
                label = { Text(chip, fontSize = 12.sp) }
            )
        }
    }
}

@Composable
fun TypingIndicatorBubble() {
    val isDark = isSystemInDarkTheme()
    val bubbleColor = if (isDark) AiBlueDark.copy(alpha = 0.2f) else AiBlueLight.copy(alpha = 0.15f)

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Box(
            modifier = Modifier
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomEnd = 16.dp,
                        bottomStart = 4.dp
                    )
                )
                .background(bubbleColor)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.SmartToy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Sensei thinking...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubbleItem(
    message: Message,
    onImageClick: (String) -> Unit,
    onSaveToKb: (GuideData) -> Unit,
    onFeedback: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val isUser = message.role == "user"
    val isDark = isSystemInDarkTheme()

    val bubbleColor = if (isUser) {
        if (isDark) TechGreenDark else TechGreenLight
    } else {
        if (isDark) AiBlueDark.copy(alpha = 0.2f) else AiBlueLight.copy(alpha = 0.12f)
    }

    val textColor = if (isUser) Color.White else MaterialTheme.colorScheme.onSurface

    val bubbleShape = if (isUser) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp)
    } else {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 4.dp)
    }

    val formattedTime = remember(message.timestamp) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 340.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(bubbleShape)
                    .background(bubbleColor)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = {
                            if (!isUser) {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("AI Diagnostics", message.text ?: "")
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Copied AI message to clipboard", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    .padding(12.dp)
            ) {
                Column {
                    // Image attachment
                    message.imageUrl?.let { url ->
                        AsyncImage(
                            model = url,
                            contentDescription = "Attachment",
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onImageClick(url) }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Structured Repair Guide Card
                    if (message.guideData != null) {
                        RepairGuideCard(
                            guide = message.guideData,
                            onImageClick = onImageClick,
                            onSaveToKb = { onSaveToKb(message.guideData) }
                        )
                    } else if (!message.text.isNullOrBlank()) {
                        Text(
                            text = message.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = textColor
                        )
                    }
                }
            }

            // Timestamp below message
            Text(
                text = formattedTime,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )

            // Was this helpful feedback prompt for AI answers
            if (!isUser && message.guideData != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "Was this helpful?",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onFeedback(true) },
                                modifier = Modifier.height(32.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Text("Yes solved", fontSize = 11.sp)
                            }
                            OutlinedButton(
                                onClick = { onFeedback(false) },
                                modifier = Modifier.height(32.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Text("No continue", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RepairGuideCard(
    guide: GuideData,
    onImageClick: (String) -> Unit,
    onSaveToKb: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(true) }
    val context = LocalContext.current

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
            ) {
                Text(
                    text = "🔧 ${guide.device} — ${guide.problem}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Difficulty & Time
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("⚠️ Difficulty ${guide.difficulty}", style = MaterialTheme.typography.labelSmall, color = WarningOrange, fontWeight = FontWeight.Bold)
                Text("⏱ ${guide.timeEstimate}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                // Tools List
                if (guide.tools.isNotEmpty()) {
                    Text("📋 Tools required:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    guide.tools.forEach { tool ->
                        Text("• $tool", style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Likely Causes
                if (guide.causes.isNotEmpty()) {
                    Text("🎯 Likely Causes:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    guide.causes.forEachIndexed { idx, cause ->
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Text("${idx + 1}️⃣ ${cause.description} (${cause.probability}%)", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            Text("   Check: ${cause.checkMethod}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
                            Text("   Normal: ${cause.normalValue}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
                            Text("   Fix: ${cause.fixMethod}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Step by Step
                if (guide.steps.isNotEmpty()) {
                    Text("🔧 Step-by-Step:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    guide.steps.forEach { step ->
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Text("Step ${step.stepNumber}: ${step.title}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            Text(step.description, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Pro Tip
                guide.proTip?.let { tip ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                            .padding(8.dp)
                    ) {
                        Text("💡 Pro Tip: $tip", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Risks
                guide.risks?.let { risk ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
                            .padding(8.dp)
                    ) {
                        Text("⚠️ Risks: $risk", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Links
                if (guide.links.isNotEmpty()) {
                    Text("🔗 Links:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    guide.links.forEach { url ->
                        Text(
                            text = url,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Opening link: $url", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Save to Knowledge Base Button
                Button(
                    onClick = onSaveToKb,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.BookmarkAdd, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save to Knowledge Base", fontSize = 12.sp)
                }
            }
        }
    }
}
