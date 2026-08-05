package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.model.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import java.util.UUID

class TechMateRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("tech_mate_prefs", Context.MODE_PRIVATE)

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val guideAdapter = moshi.adapter(GuideData::class.java)

    // State flows for reactive UI updates
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _isDarkMode = MutableStateFlow(prefs.getBoolean("dark_mode", false))
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    private val _chats = MutableStateFlow<List<Chat>>(emptyList())
    val chats: StateFlow<List<Chat>> = _chats.asStateFlow()

    private val _messagesMap = MutableStateFlow<Map<String, List<Message>>>(emptyMap())
    val messagesMap: StateFlow<Map<String, List<Message>>> = _messagesMap.asStateFlow()

    private val _knowledgeBase = MutableStateFlow<List<KnowledgeBaseEntry>>(emptyList())
    val knowledgeBase: StateFlow<List<KnowledgeBaseEntry>> = _knowledgeBase.asStateFlow()

    private val _users = MutableStateFlow<List<User>>(emptyList())
    val users: StateFlow<List<User>> = _users.asStateFlow()

    init {
        seedInitialData()
    }

    private fun seedInitialData() {
        // Seed users if empty
        val storedUsersJson = prefs.getString("users_json", null)
        val loadedUsers = mutableListOf<User>()
        if (storedUsersJson == null) {
            val adminUser = User(
                username = "admin",
                passwordHash = hashPassword("admin123"),
                role = "admin",
                name = "Lead Technician Sensei"
            )
            val masterUser = User(
                username = "tech",
                passwordHash = hashPassword("tech123"),
                role = "master",
                name = "Alex Tech"
            )
            loadedUsers.add(adminUser)
            loadedUsers.add(masterUser)
            saveUsersToPrefs(loadedUsers)
        } else {
            // simple parsing or standard users
            val defaultAdmin = User(
                username = "admin",
                passwordHash = hashPassword("admin123"),
                role = "admin",
                name = "Lead Technician Sensei"
            )
            loadedUsers.add(defaultAdmin)
        }
        _users.value = loadedUsers

        // Auto login saved user or admin
        val lastUserUsername = prefs.getString("last_username", "admin")
        _currentUser.value = loadedUsers.find { it.username == lastUserUsername } ?: loadedUsers.firstOrNull()

        // Seed Knowledge Base
        val seedGuides = listOf(
            KnowledgeBaseEntry(
                brand = "samsung",
                model = "a51",
                problem = "Not Charging Moisture Detected",
                addedBy = "Sensei",
                guideData = GuideData(
                    device = "Samsung A51",
                    problem = "Not Charging Moisture Detected",
                    difficulty = "Medium",
                    timeEstimate = "30 to 40 minutes",
                    tools = listOf("Multimeter", "Heat gun 100°C", "Isopropyl alcohol 99%", "Tweezers", "USB tester"),
                    causes = listOf(
                        Cause("Oxidized Type-C port", 60, "Inspect port with flashlight", "Clean gold contacts no green or white residue", "Clean with alcohol and soft brush"),
                        Cause("Oxidized inter-board connector", 25, "Press firmly on center of back cover", "Charging appears when pressed means connector issue", "Heat area and reflow connector"),
                        Cause("Damaged bottom board", 15, "Measure voltage at battery connector", "3.7 to 4.2 volts", "Replace bottom board")
                    ),
                    steps = listOf(
                        Step(1, "External inspection", "Inspect Type-C port with flashlight, green or white residue means oxidation, clean with alcohol and soft brush, blow with compressed air."),
                        Step(2, "Quick inter-board check", "Press firmly on center of back cover just below camera bump, connect charger, if charging indicator appears problem is in inter-board connector."),
                        Step(3, "Disassembly", "If steps 1 and 2 do not help, heat back cover to 100°C, remove, inspect bottom board for moisture traces, measure voltage at battery connector.")
                    ),
                    proTip = "A51 weak spot is inter-board connector on main board, after moisture exposure it oxidizes first, under microscope damage is often visible to naked eye.",
                    risks = "Do not heat back cover above 100°C or LCD spots appear, when replacing Type-C port do not overheat because traces are thin and lift easily.",
                    links = listOf("https://techmate.app/schematics/samsung-a51", "https://techmate.app/disassembly/samsung-a51")
                )
            ),
            KnowledgeBaseEntry(
                brand = "redmi",
                model = "9t",
                problem = "No Power No Charging",
                addedBy = "Sensei",
                guideData = GuideData(
                    device = "Redmi 9T",
                    problem = "No Power No Charging",
                    difficulty = "Medium",
                    timeEstimate = "20 to 30 minutes",
                    tools = listOf("Multimeter", "Soldering iron", "Tweezers", "Shield cutter"),
                    causes = listOf(
                        Cause("Factory defect capacitor short under shield", 80, "Measure resistance on VBUS line", "Greater than 100 kOhms", "Remove bottom shield and remove shorted capacitor"),
                        Cause("PMIC fault PM660", 20, "Check thermal camera under boot", "No hot spots", "Reflow PMIC")
                    ),
                    steps = listOf(
                        Step(1, "Remove bottom board", "Unscrew and remove the bottom board."),
                        Step(2, "Remove shield", "On the bottom-left side of the board remove the metal shield."),
                        Step(3, "Find the short", "Under the shield you will see a capacitor pressing against the shield, this is a known factory defect, desolder the capacitor or cut away the shield section above it.")
                    ),
                    proTip = "Do not waste time probing everything, on 9T 80% of no-power cases are this exact capacitor, go straight for the shield.",
                    risks = "Be careful when cutting shield near PMIC to avoid damaging nearby traces.",
                    links = listOf("https://techmate.app/schematics/redmi-9t")
                )
            ),
            KnowledgeBaseEntry(
                brand = "apple",
                model = "iphone 14",
                problem = "Stuck on Apple Logo Boot Loop",
                addedBy = "Sensei",
                guideData = GuideData(
                    device = "iPhone 14",
                    problem = "Stuck on Apple Logo Boot Loop",
                    difficulty = "Hard",
                    timeEstimate = "45 to 60 minutes",
                    tools = listOf("Hot air station", "Microscope", "Multimeter", "iMazing / iTunes"),
                    causes = listOf(
                        Cause("Corrupted NAND or proximity sensor flex short", 70, "Disconnect upper sensor flex and power on", "Normal boot to lockscreen", "Replace upper flex assembly"),
                        Cause("Inter-board pads cracked from drop", 30, "Inspect motherboard sandwich under microscope", "Flat contact pads without fracture lines", "Split sandwich and reball sandwich layer")
                    ),
                    steps = listOf(
                        Step(1, "Isolate front flex", "Open device and disconnect earpiece sensor flex."),
                        Step(2, "Test boot", "Power on device while flex is disconnected. If it boots, flex is shorted."),
                        Step(3, "Motherboard inspection", "If flex is fine, check motherboard sandwich for short on 1V8 line.")
                    ),
                    proTip = "Proximity sensor module gets moisture from ear speaker mesh causing 1V8 short. Always disconnect top flex first!",
                    risks = "Face ID flood illuminator is paired to motherboard; transfer component carefully when replacing flex.",
                    links = listOf("https://techmate.app/schematics/iphone14")
                )
            ),
            // Schematics entries
            KnowledgeBaseEntry(
                brand = "samsung",
                model = "a51",
                problem = "Board Schematic & PCB Layout",
                addedBy = "System",
                isSchematic = true,
                schematicImageUrl = "https://images.unsplash.com/photo-1518770660439-4636190af475?w=800&auto=format&fit=crop&q=60",
                guideData = GuideData("Samsung A51", "Board Schematic", "Easy", "N/A", emptyList(), emptyList(), emptyList())
            ),
            KnowledgeBaseEntry(
                brand = "apple",
                model = "iphone 14",
                problem = "Motherboard Component Layout",
                addedBy = "System",
                isSchematic = true,
                schematicImageUrl = "https://images.unsplash.com/photo-1600585154340-be6161a56a0c?w=800&auto=format&fit=crop&q=60",
                guideData = GuideData("iPhone 14", "Motherboard Component Layout", "Easy", "N/A", emptyList(), emptyList(), emptyList())
            )
        )
        _knowledgeBase.value = seedGuides

        // Seed sample chat
        val sampleChatId = UUID.randomUUID().toString()
        val sampleChat = Chat(
            id = sampleChatId,
            title = "Samsung A51 — Not Charging Moisture",
            lastMessage = "Cleaned Type-C port and verified 4.2V charging current!",
            lastMessageTime = System.currentTimeMillis() - 1200000,
            isPinned = true
        )
        val sampleMessages = listOf(
            Message(
                chatId = sampleChatId,
                role = "user",
                type = "text",
                text = "Samsung A51 shows moisture in charging port after water drop. Won't charge.",
                timestamp = System.currentTimeMillis() - 3600000
            ),
            Message(
                chatId = sampleChatId,
                role = "ai",
                type = "guide",
                guideData = seedGuides[0].guideData,
                text = "🔧 Samsung A51 — Not Charging Moisture Detected\n⚠️ Difficulty Medium\n⏱ 30 to 40 minutes",
                timestamp = System.currentTimeMillis() - 3500000
            )
        )

        _chats.value = listOf(sampleChat)
        _messagesMap.value = mapOf(sampleChatId to sampleMessages)
    }

    private fun saveUsersToPrefs(usersList: List<User>) {
        // Save simplicity
        prefs.edit().putString("users_count", usersList.size.toString()).apply()
    }

    fun toggleDarkMode() {
        val newMode = !_isDarkMode.value
        _isDarkMode.value = newMode
        prefs.edit().putBoolean("dark_mode", newMode).apply()
    }

    fun hashPassword(password: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(password.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    // User management
    fun login(usernameInput: String, passwordInput: String): Result<User> {
        val u = _users.value.find { it.username.equals(usernameInput.trim(), ignoreCase = true) }
        if (u == null) return Result.failure(Exception("User not found"))
        if (u.passwordHash != hashPassword(passwordInput)) return Result.failure(Exception("Invalid password"))
        _currentUser.value = u
        prefs.edit().putString("last_username", u.username).apply()
        return Result.success(u)
    }

    fun registerAdmin(usernameInput: String, passwordInput: String, nameInput: String): Result<User> {
        val newUser = User(
            username = usernameInput.trim(),
            passwordHash = hashPassword(passwordInput),
            role = "admin",
            name = nameInput.trim()
        )
        val list = _users.value.toMutableList()
        list.add(newUser)
        _users.value = list
        _currentUser.value = newUser
        prefs.edit().putString("last_username", newUser.username).apply()
        return Result.success(newUser)
    }

    fun addUser(usernameInput: String, passwordInput: String, nameInput: String, roleInput: String): Result<User> {
        if (_users.value.size >= 10) {
            return Result.failure(Exception("User limit reached. Maximum 10 users permitted."))
        }
        if (_users.value.any { it.username.equals(usernameInput.trim(), ignoreCase = true) }) {
            return Result.failure(Exception("Username already exists"))
        }
        val newUser = User(
            username = usernameInput.trim(),
            passwordHash = hashPassword(passwordInput),
            role = roleInput,
            name = nameInput.trim()
        )
        val list = _users.value.toMutableList()
        list.add(newUser)
        _users.value = list
        return Result.success(newUser)
    }

    fun removeUser(userId: String): Boolean {
        val list = _users.value.toMutableList()
        val target = list.find { it.id == userId } ?: return false
        if (target.role == "admin" && list.count { it.role == "admin" } <= 1) {
            return false // Cannot remove last admin
        }
        list.remove(target)
        _users.value = list
        return true
    }

    fun updateUserName(newName: String) {
        val user = _currentUser.value ?: return
        val updated = user.copy(name = newName)
        _currentUser.value = updated
        val list = _users.value.toMutableList()
        val index = list.indexOfFirst { it.id == user.id }
        if (index != -1) {
            list[index] = updated
            _users.value = list
        }
    }

    fun logout() {
        _currentUser.value = null
        prefs.edit().remove("last_username").apply()
    }

    // Chat CRUD
    fun createChat(initialTitle: String = "New Repair Inquiry"): Chat {
        val newChat = Chat(
            title = initialTitle,
            lastMessage = "Started repair session...",
            lastMessageTime = System.currentTimeMillis()
        )
        val updatedChats = listOf(newChat) + _chats.value
        _chats.value = updatedChats
        _messagesMap.value = _messagesMap.value + (newChat.id to emptyList())
        return newChat
    }

    fun deleteChat(chatId: String) {
        _chats.value = _chats.value.filter { it.id != chatId }
        val map = _messagesMap.value.toMutableMap()
        map.remove(chatId)
        _messagesMap.value = map
    }

    fun togglePinChat(chatId: String) {
        _chats.value = _chats.value.map {
            if (it.id == chatId) it.copy(isPinned = !it.isPinned) else it
        }.sortedWith(compareByDescending<Chat> { it.isPinned }.thenByDescending { it.lastMessageTime })
    }

    fun updateChatTitle(chatId: String, newTitle: String) {
        _chats.value = _chats.value.map {
            if (it.id == chatId) it.copy(title = newTitle) else it
        }
    }

    fun addMessage(message: Message) {
        val currentList = _messagesMap.value[message.chatId] ?: emptyList()
        val updatedList = currentList + message
        _messagesMap.value = _messagesMap.value + (message.chatId to updatedList)

        // update chat last message
        val lastText = when (message.type) {
            "image" -> "[Image Attachment]"
            "guide" -> message.guideData?.let { "🔧 ${it.device} — ${it.problem}" } ?: (message.text ?: "")
            else -> message.text ?: ""
        }

        _chats.value = _chats.value.map {
            if (it.id == message.chatId) {
                it.copy(
                    lastMessage = lastText.take(60),
                    lastMessageTime = message.timestamp
                )
            } else it
        }.sortedWith(compareByDescending<Chat> { it.isPinned }.thenByDescending { it.lastMessageTime })
    }

    // Knowledge Base CRUD
    fun addKnowledgeBaseEntry(entry: KnowledgeBaseEntry): Result<KnowledgeBaseEntry> {
        val normalizedBrand = entry.brand.trim().lowercase()
        val normalizedModel = entry.model.trim().lowercase()
        val normalizedProblem = entry.problem.trim()

        val exists = _knowledgeBase.value.any {
            it.brand.lowercase() == normalizedBrand &&
                    it.model.lowercase() == normalizedModel &&
                    it.problem.equals(normalizedProblem, ignoreCase = true)
        }

        val formattedEntry = entry.copy(
            brand = normalizedBrand,
            model = normalizedModel,
            problem = normalizedProblem
        )

        val list = _knowledgeBase.value.toMutableList()
        if (exists) {
            val index = list.indexOfFirst {
                it.brand.lowercase() == normalizedBrand &&
                        it.model.lowercase() == normalizedModel &&
                        it.problem.equals(normalizedProblem, ignoreCase = true)
            }
            if (index != -1) {
                list[index] = formattedEntry
            } else {
                list.add(0, formattedEntry)
            }
        } else {
            list.add(0, formattedEntry)
        }
        _knowledgeBase.value = list
        return Result.success(formattedEntry)
    }

    fun deleteKnowledgeBaseEntry(entryId: String) {
        _knowledgeBase.value = _knowledgeBase.value.filter { it.id != entryId }
    }
}
