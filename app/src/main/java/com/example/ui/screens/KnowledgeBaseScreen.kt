package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.example.model.Cause
import com.example.model.GuideData
import com.example.model.KnowledgeBaseEntry
import com.example.model.Step
import com.example.ui.theme.AiBlueLight
import com.example.ui.theme.CopperTrace

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun KnowledgeBaseScreen(
    repository: TechMateRepository,
    initialPreFillGuide: GuideData? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val knowledgeBase by repository.knowledgeBase.collectAsState()
    val currentUser by repository.currentUser.collectAsState()

    var selectedTabIndex by remember { mutableIntStateOf(0) } // 0 = Guides, 1 = Schematics
    var searchQuery by remember { mutableStateOf("") }

    // Navigation drill down state
    var selectedBrand by remember { mutableStateOf<String?>(null) }
    var selectedModel by remember { mutableStateOf<String?>(null) }
    var selectedEntryDetail by remember { mutableStateOf<KnowledgeBaseEntry?>(null) }

    var showAddModal by remember { mutableStateOf(initialPreFillGuide != null) }
    var preFillGuideState by remember { mutableStateOf(initialPreFillGuide) }
    var fullscreenSchematicUrl by remember { mutableStateOf<String?>(null) }

    var entryToDelete by remember { mutableStateOf<KnowledgeBaseEntry?>(null) }

    // Filtered entries based on tab & search
    val entriesForTab = remember(knowledgeBase, selectedTabIndex) {
        knowledgeBase.filter { if (selectedTabIndex == 0) !it.isSchematic else it.isSchematic }
    }

    val filteredEntries = remember(entriesForTab, searchQuery) {
        if (searchQuery.isBlank()) entriesForTab
        else entriesForTab.filter {
            it.brand.contains(searchQuery, ignoreCase = true) ||
                    it.model.contains(searchQuery, ignoreCase = true) ||
                    it.problem.contains(searchQuery, ignoreCase = true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (selectedBrand == null) "iFixit Repair Knowledge Base" else "$selectedBrand ${selectedModel ?: ""}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedEntryDetail != null) {
                            selectedEntryDetail = null
                        } else if (selectedModel != null) {
                            selectedModel = null
                        } else if (selectedBrand != null) {
                            selectedBrand = null
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (currentUser?.role != "viewer") {
                        IconButton(onClick = {
                            preFillGuideState = null
                            showAddModal = true
                        }) {
                            Icon(Icons.Default.Add, contentDescription = "Add Guide")
                        }
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
            // TabBar (Guides / Schematics)
            TabRow(selectedTabIndex = selectedTabIndex) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = {
                        selectedTabIndex = 0
                        selectedBrand = null
                        selectedModel = null
                        selectedEntryDetail = null
                    },
                    text = { Text("Guides", fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.MenuBook, contentDescription = null) }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = {
                        selectedTabIndex = 1
                        selectedBrand = null
                        selectedModel = null
                        selectedEntryDetail = null
                    },
                    text = { Text("Schematics", fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.Memory, contentDescription = null) }
                )
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search brand, model, or problem...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            // Content drill-down
            when {
                // 1. Detailed View of single entry
                selectedEntryDetail != null -> {
                    val entry = selectedEntryDetail!!
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        RepairGuideCard(
                            guide = entry.guideData,
                            onImageClick = { fullscreenSchematicUrl = it },
                            onSaveToKb = {}
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Added by: ${entry.addedBy}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                }

                // 2. Model selected -> List of problems
                selectedModel != null && selectedBrand != null -> {
                    val problemEntries = filteredEntries.filter {
                        it.brand.equals(selectedBrand, ignoreCase = true) &&
                                it.model.equals(selectedModel, ignoreCase = true)
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        items(problemEntries, key = { it.id }) { entry ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                                    .combinedClickable(
                                        onClick = {
                                            if (entry.isSchematic && entry.schematicImageUrl != null) {
                                                fullscreenSchematicUrl = entry.schematicImageUrl
                                            } else {
                                                selectedEntryDetail = entry
                                            }
                                        },
                                        onLongClick = {
                                            if (currentUser?.role != "viewer") {
                                                entryToDelete = entry
                                            }
                                        }
                                    ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (entry.isSchematic) Icons.Default.Memory else Icons.Default.BuildCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(entry.problem, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Text("${entry.guideData.difficulty} • ${entry.guideData.timeEstimate}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                    }
                                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                                }
                            }
                        }
                    }
                }

                // 3. Brand selected -> List of models
                selectedBrand != null -> {
                    val modelsForBrand = filteredEntries
                        .filter { it.brand.equals(selectedBrand, ignoreCase = true) }
                        .map { it.displayModel }
                        .distinct()

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        items(modelsForBrand) { modelName ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                                    .clickable { selectedModel = modelName },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.PhoneIphone, contentDescription = null, tint = CopperTrace)
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(modelName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.weight(1f))
                                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                                }
                            }
                        }
                    }
                }

                // 4. Default -> Brand Grid
                else -> {
                    val brandCounts = remember(filteredEntries) {
                        filteredEntries.groupBy { it.displayBrand }.mapValues { it.value.size }
                    }

                    if (brandCounts.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No guides or schematics found.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(brandCounts.keys.toList()) { brandName ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(110.dp)
                                        .clickable { selectedBrand = brandName },
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(16.dp),
                                        verticalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(
                                                imageVector = when (brandName.lowercase()) {
                                                    "apple" -> Icons.Default.PhoneIphone
                                                    "samsung" -> Icons.Default.Smartphone
                                                    "xiaomi", "redmi" -> Icons.Default.MobileFriendly
                                                    else -> Icons.Default.Devices
                                                },
                                                contentDescription = brandName,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(28.dp)
                                            )
                                            Badge { Text("${brandCounts[brandName]}", fontWeight = FontWeight.Bold) }
                                        }
                                        Text(
                                            text = brandName,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Add / Edit Entry Dialog Modal
        if (showAddModal) {
            AddKnowledgeBaseModal(
                preFillGuide = preFillGuideState,
                repository = repository,
                onDismiss = { showAddModal = false },
                onSaved = { showAddModal = false }
            )
        }

        // Fullscreen Schematic Zoom Dialog
        fullscreenSchematicUrl?.let { url ->
            Dialog(onDismissRequest = { fullscreenSchematicUrl = null }) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .clickable { fullscreenSchematicUrl = null },
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = url,
                        contentDescription = "Board Schematic",
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                    )
                    IconButton(
                        onClick = { fullscreenSchematicUrl = null },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
            }
        }

        // Delete Confirmation Dialog
        entryToDelete?.let { entry ->
            AlertDialog(
                onDismissRequest = { entryToDelete = null },
                title = { Text("Delete Entry?") },
                text = { Text("Are you sure you want to delete guide '${entry.problem}' for ${entry.displayBrand} ${entry.displayModel}?") },
                confirmButton = {
                    TextButton(onClick = {
                        repository.deleteKnowledgeBaseEntry(entry.id)
                        entryToDelete = null
                        Toast.makeText(context, "Deleted guide", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { entryToDelete = null }) { Text("Cancel") }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddKnowledgeBaseModal(
    preFillGuide: GuideData?,
    repository: TechMateRepository,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    var brand by remember { mutableStateOf(preFillGuide?.device?.split(" ")?.firstOrNull() ?: "") }
    var model by remember { mutableStateOf(preFillGuide?.device?.split(" ")?.drop(1)?.joinToString(" ") ?: "") }
    var problem by remember { mutableStateOf(preFillGuide?.problem ?: "") }
    var difficulty by remember { mutableStateOf(preFillGuide?.difficulty ?: "Medium") }
    var timeEst by remember { mutableStateOf(preFillGuide?.timeEstimate ?: "30 minutes") }

    var toolsText by remember { mutableStateOf(preFillGuide?.tools?.joinToString(", ") ?: "Multimeter, Heat gun 100°C, Isopropyl alcohol 99%") }
    var proTip by remember { mutableStateOf(preFillGuide?.proTip ?: "") }
    var risks by remember { mutableStateOf(preFillGuide?.risks ?: "") }

    var showOverwritePrompt by remember { mutableStateOf(false) }

    fun executeSave() {
        if (brand.isBlank() || model.isBlank() || problem.isBlank()) {
            Toast.makeText(context, "Brand, Model, and Problem are required", Toast.LENGTH_SHORT).show()
            return
        }

        val parsedTools = toolsText.split(",").map { it.trim() }.filter { it.isNotBlank() }
        val guide = GuideData(
            device = "$brand $model".trim(),
            problem = problem,
            difficulty = difficulty,
            timeEstimate = timeEst,
            tools = parsedTools,
            causes = preFillGuide?.causes ?: listOf(Cause("Corrosion or line short", 70, "Inspect line in diode mode", "> 0.350V", "Reflow or clean joints")),
            steps = preFillGuide?.steps ?: listOf(Step(1, "Diagnostic & Clean", "Examine board under microscope and clean affected contacts.")),
            proTip = proTip.ifBlank { null },
            risks = risks.ifBlank { null },
            links = preFillGuide?.links ?: listOf("https://techmate.app/schematics")
        )

        val entry = KnowledgeBaseEntry(
            brand = brand,
            model = model,
            problem = problem,
            guideData = guide,
            addedBy = repository.currentUser.value?.name ?: "Technician"
        )

        repository.addKnowledgeBaseEntry(entry)
        Toast.makeText(context, "Saved guide to Knowledge Base!", Toast.LENGTH_SHORT).show()
        onSaved()
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .wrapContentHeight(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("Add Repair Guide", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = brand,
                    onValueChange = { brand = it },
                    label = { Text("Brand (e.g., Samsung, Apple)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("Model (e.g., A51, iPhone 14)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = problem,
                    onValueChange = { problem = it },
                    label = { Text("Problem (e.g., Not Charging Moisture)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Easy", "Medium", "Hard").forEach { diff ->
                        FilterChip(
                            selected = difficulty == diff,
                            onClick = { difficulty = diff },
                            label = { Text(diff) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = toolsText,
                    onValueChange = { toolsText = it },
                    label = { Text("Tools (comma separated)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = proTip,
                    onValueChange = { proTip = it },
                    label = { Text("Pro Tip (model-specific hack)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = risks,
                    onValueChange = { risks = it },
                    label = { Text("Risks / Warnings") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { executeSave() }) {
                        Text("SAVE GUIDE")
                    }
                }
            }
        }
    }
}
