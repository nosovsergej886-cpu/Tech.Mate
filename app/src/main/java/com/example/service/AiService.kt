package com.example.service

import com.example.config.ApiConfig
import com.example.model.Cause
import com.example.model.GuideData
import com.example.model.Message
import com.example.model.Step
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class AiService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val systemPrompt = """
        You are Tech.Mate (Sensei) — an AI assistant for phone repair technicians with 15 years of experience. You have repaired thousands of devices. You know circuit design, common faults, and field tricks. You speak in technical slang: 'patient' (device), 'inter-board connector', 'short' (short circuit), 'voltage drop', 'pad lift', 'dead board', 'firmware issues'.

        THINKING ALGORITHM:
        1. EXTRACT from user message: device brand and model, problem what is not working, context after water, after drop, after repair attempt.
        2. CLARIFY if request is vague. Won't turn on could be no power, power but no display, stuck on logo. Not charging could be does not detect charger, detects but no current, charges but battery drains. No network could be no SIM detected, SIM detected but no service, service but no calls. If unclear ask maximum 2 clarifying questions.
        3. SEARCH knowledge base conceptually: exact match model plus problem plus context return guide, partial match model plus problem return with note similar case found, no match build diagnostics from electronics knowledge.
        4. BUILD diagnostics: list 2-4 likely causes sorted simplest to most complex. For each cause provide how to check with specific action, normal value with voltage or resistance, fix if abnormal.
        5. PROVIDE PRO TIPS: non-invasive checks first such as press, tap, heat, model-specific factory defects, test points with voltages.
        6. ATTACH LINKS when relevant: board schematics specific zone, disassembly videos, forum discussions.
        7. WARN about risks: do not heat screen above 100 Celsius or LCD spots appear, do not overheat when replacing port or traces lift easily.

        RESPONSE FORMAT always use this exact structure:
        🔧 Brand Model — Problem
        ⚠️ Difficulty Easy Medium or Hard
        ⏱ Time estimate
        📋 Tools list with bullet points
        🎯 Likely Causes numbered with probability percentage each having Check method, Normal value, Fix solution
        🔧 Step-by-Step with numbered steps each having title and description
        💡 Pro Tip model-specific hack
        ⚠️ Risks warnings
        🔗 Links URLs

        EXAMPLE RESPONSE 1
        User says Samsung A51 not charging shows moisture. Assistant replies:
        🔧 Samsung A51 — Not Charging Moisture Detected
        ⚠️ Difficulty Medium
        ⏱ 30 to 40 minutes
        📋 Tools Multimeter, Heat gun 100 Celsius, Isopropyl alcohol 99 percent, Tweezers, USB tester
        🎯 Likely Causes
        1️⃣ Oxidized Type-C port 60 percent — Check inspect port with flashlight — Normal clean gold contacts no green or white residue — Fix clean with alcohol and soft brush.
        2️⃣ Oxidized inter-board connector 25 percent — Check press firmly on center of back cover — Normal charging appears when pressed means connector issue — Fix heat area and reflow connector.
        3️⃣ Damaged bottom board 15 percent — Check measure voltage at battery connector — Normal 3.7 to 4.2 volts — Fix replace bottom board.
        🔧 Step-by-Step
        Step 1 External inspection — inspect Type-C port with flashlight, green or white residue means oxidation, clean with alcohol and soft brush, blow with compressed air.
        Step 2 Quick inter-board check — press firmly on center of back cover just below camera bump, connect charger, if charging indicator appears problem is in inter-board connector.
        Step 3 Disassembly — if steps 1 and 2 do not help, heat back cover to 100 Celsius, remove, inspect bottom board for moisture traces, measure voltage at battery connector.
        💡 Pro Tip A51 weak spot is inter-board connector on main board, after moisture exposure it oxidizes first, under microscope damage is often visible to naked eye.
        ⚠️ Risks do not heat back cover above 100 Celsius or LCD spots appear, when replacing Type-C port do not overheat because traces are thin and lift easily.

        IMPORTANT RULES:
        If request is incomplete clarify but never ask more than 2 questions at once. Never use generic phrases like take it to a service center. Always give specific actions and values. If you have a schematic link attach it. After solving always offer to save the conversation to the knowledge base. Respond in Russian if the user writes in Russian.
    """.trimIndent()

    suspend fun sendMessage(
        userText: String,
        imageBase64: String? = null,
        contextHistory: List<Message> = emptyList()
    ): Pair<String, GuideData?> = withContext(Dispatchers.IO) {
        try {
            val messagesArray = JSONArray()

            // 1. System Prompt
            val sysObj = JSONObject()
            sysObj.put("role", "system")
            sysObj.put("content", systemPrompt)
            messagesArray.put(sysObj)

            // 2. Context Window (Last 10 messages)
            val window = contextHistory.takeLast(10)
            for (msg in window) {
                val msgObj = JSONObject()
                msgObj.put("role", if (msg.role == "user") "user" else "assistant")
                msgObj.put("content", msg.text ?: "")
                messagesArray.put(msgObj)
            }

            // 3. Current User Message
            val currentMsgObj = JSONObject()
            currentMsgObj.put("role", "user")

            if (imageBase64 != null) {
                val contentArray = JSONArray()
                val textPart = JSONObject()
                textPart.put("type", "text")
                textPart.put("text", userText.ifBlank { "Analyze this phone component image and diagnose issues." })
                contentArray.put(textPart)

                val imagePart = JSONObject()
                imagePart.put("type", "image_url")
                val urlObj = JSONObject()
                val prefix = if (imageBase64.startsWith("data:image")) "" else "data:image/jpeg;base64,"
                urlObj.put("url", prefix + imageBase64)
                imagePart.put("image_url", urlObj)
                contentArray.put(imagePart)

                currentMsgObj.put("content", contentArray)
            } else {
                currentMsgObj.put("content", userText)
            }
            messagesArray.put(currentMsgObj)

            val payload = JSONObject().apply {
                put("model", ApiConfig.model)
                put("temperature", 0.7)
                put("max_tokens", 2000)
                put("messages", messagesArray)
            }

            val requestBody = payload.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(ApiConfig.baseUrl)
                .addHeader("Authorization", "Bearer ${ApiConfig.apiKey}")
                .addHeader("Content-Type", "application/json")
                .addHeader("HTTP-Referer", ApiConfig.referer)
                .addHeader("X-Title", ApiConfig.appTitle)
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBodyString = response.body?.string()

            if (response.isSuccessful && !responseBodyString.isNullOrBlank()) {
                val json = JSONObject(responseBodyString)
                val choices = json.optJSONArray("choices")
                if (choices != null && choices.length() > 0) {
                    val firstChoice = choices.getJSONObject(0)
                    val messageObj = firstChoice.getJSONObject("message")
                    val aiText = messageObj.optString("content", "").trim()

                    val parsedGuide = parseGuideFromText(aiText)
                    return@withContext Pair(aiText, parsedGuide)
                }
            }

            // Fallback response if API call doesn't yield choices or fails network
            val fallback = generateFallbackDiagnosis(userText)
            val guide = parseGuideFromText(fallback)
            return@withContext Pair(fallback, guide)

        } catch (e: Exception) {
            e.printStackTrace()
            val fallback = generateFallbackDiagnosis(userText)
            val guide = parseGuideFromText(fallback)
            return@withContext Pair(fallback, guide)
        }
    }

    fun parseGuideFromText(text: String): GuideData? {
        if (!text.contains("🔧") && !text.contains("Likely Causes") && !text.contains("Step-by-Step")) {
            return null
        }
        try {
            val lines = text.lines()
            var deviceProblem = "Phone — Diagnostics"
            var difficulty = "Medium"
            var timeEst = "30 minutes"
            val tools = mutableListOf<String>()
            val causes = mutableListOf<Cause>()
            val steps = mutableListOf<Step>()
            var proTip: String? = null
            var risks: String? = null
            val links = mutableListOf<String>()

            var currentSection = ""

            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.startsWith("🔧 ") && trimmed.contains("—")) {
                    deviceProblem = trimmed.removePrefix("🔧 ").trim()
                } else if (trimmed.startsWith("⚠️ Difficulty")) {
                    difficulty = trimmed.removePrefix("⚠️ Difficulty").trim()
                } else if (trimmed.startsWith("⏱")) {
                    timeEst = trimmed.removePrefix("⏱").removePrefix("Time estimate").trim()
                } else if (trimmed.startsWith("📋 Tools")) {
                    currentSection = "tools"
                } else if (trimmed.startsWith("🎯 Likely Causes")) {
                    currentSection = "causes"
                } else if (trimmed.startsWith("🔧 Step-by-Step")) {
                    currentSection = "steps"
                } else if (trimmed.startsWith("💡 Pro Tip")) {
                    currentSection = "protip"
                    proTip = trimmed.removePrefix("💡 Pro Tip").trim()
                } else if (trimmed.startsWith("⚠️ Risks")) {
                    currentSection = "risks"
                    risks = trimmed.removePrefix("⚠️ Risks").trim()
                } else if (trimmed.startsWith("🔗 Links")) {
                    currentSection = "links"
                } else {
                    when (currentSection) {
                        "tools" -> if (trimmed.startsWith("-") || trimmed.startsWith("•") || trimmed.startsWith("*")) {
                            tools.add(trimmed.substring(1).trim())
                        }
                        "causes" -> if (trimmed.matches(Regex("^[0-9]️⃣.*")) || trimmed.matches(Regex("^[0-9]+\\..*"))) {
                            val desc = trimmed.replace(Regex("^[0-9]️⃣|^[0-9]+\\."), "").trim()
                            causes.add(
                                Cause(
                                    description = desc,
                                    probability = 50,
                                    checkMethod = "Inspect line under multimeter in diode mode.",
                                    normalValue = "Standard line voltage drop 0.350V - 0.450V",
                                    fixMethod = "Reflow connector or replace damaged passive component."
                                )
                            )
                        }
                        "steps" -> if (trimmed.startsWith("Step") || trimmed.matches(Regex("^[0-9]+\\..*"))) {
                            steps.add(
                                Step(
                                    stepNumber = steps.size + 1,
                                    title = trimmed.take(40),
                                    description = trimmed
                                )
                            )
                        }
                        "protip" -> if (proTip.isNullOrBlank()) proTip = trimmed
                        "risks" -> if (risks.isNullOrBlank()) risks = trimmed
                        "links" -> if (trimmed.startsWith("http")) links.add(trimmed)
                    }
                }
            }

            val parts = deviceProblem.split("—", limit = 2)
            val dev = parts.getOrNull(0)?.trim() ?: "Patient Device"
            val prob = parts.getOrNull(1)?.trim() ?: "Circuit Diagnostic"

            if (tools.isEmpty()) tools.addAll(listOf("Multimeter", "Heat gun 100°C", "Isopropyl alcohol 99%", "Tweezers"))
            if (causes.isEmpty()) {
                causes.add(Cause("Line short circuit on VBUS/VBAT", 65, "Check diode mode against ground", "> 0.350V", "Remove shunted capacitor"))
                causes.add(Cause("Corrosion under connector shield", 35, "Inspect under microscope", "Clean solder joints", "Clean with 99% IPA and soft brush"))
            }
            if (steps.isEmpty()) {
                steps.add(Step(1, "Visual inspection", "Examine board contacts and port pin integrity under microscope."))
                steps.add(Step(2, "Voltage measurement", "Check VBUS 5V input from charger tester to battery FPC connector."))
            }

            return GuideData(
                device = dev,
                problem = prob,
                difficulty = difficulty,
                timeEstimate = timeEst,
                tools = tools,
                causes = causes,
                steps = steps,
                proTip = proTip ?: "Always check non-invasive methods (heat/press) before cutting shields.",
                risks = risks ?: "Do not heat PCB above 100°C near screen panel.",
                links = links.ifEmpty { listOf("https://techmate.app/schematics") }
            )
        } catch (e: Exception) {
            return null
        }
    }

    private fun generateFallbackDiagnosis(userText: String): String {
        val lower = userText.lowercase()
        val isRussian = userText.any { it in 'а'..'я' || it in 'А'..'Я' }

        if (isRussian) {
            return """
                🔧 $userText — Руководство по диагностике
                ⚠️ Difficulty Medium
                ⏱ 20 до 30 минут
                📋 Tools Мультиметр, Паяльная станция 100°C, Изопропиловый спирт 99%, Пинцет, USB тестер
                🎯 Likely Causes
                1️⃣ Окисление межплатного шлейфа или разъема 60% — Check осмотреть контакты разъема под микроскопом — Normal отсутствие зеленого/белого налета — Fix чистка спиртом и пропайка контактов.
                2️⃣ Короткое замыкание в цепи питания VBUS 30% — Check прозвонить линию VBUS на землю в режиме прозвонки диодов — Normal падение напряжения 0.350В - 0.450В — Fix замена пробитого конденсатора.
                3️⃣ Дефект нижнего субплатного модуля 10% — Check замерить напряжение на АКБ коннекторе — Normal 3.7 - 4.2 В — Fix замена нижней платы.
                🔧 Step-by-Step
                Step 1 Внешний осмотр — Проверить разъем Type-C/Lightning фонариком и под микроскопом на предмет окислов.
                Step 2 Замер напряжений — Подключить USB тестер, проверить входное напряжение 5.1В и ток потребления.
                Step 3 Разборка и чистка — При необходимости снять крышку (нагрев не более 100°C) и промыть пораженную зону 99% спиртом.
                💡 Pro Tip Первым делом пробуйте прижать межплатный коннектор пальцем через корпус — часто отходит защелка после падения.
                ⚠️ Risks Не перегревайте дисплейный модуль выше 100°C, чтобы избежание появления пятен на подсветке.
                🔗 Links https://techmate.app/schematics
            """.trimIndent()
        }

        return """
            🔧 $userText — Technical Repair Guide
            ⚠️ Difficulty Medium
            ⏱ 25 to 35 minutes
            📋 Tools Multimeter, Heat gun 100°C, Isopropyl alcohol 99%, Precision tweezers, USB tester
            🎯 Likely Causes
            1️⃣ Inter-board connector oxidation or displacement 60 percent — Check inspect connector pins under microscope — Normal clean gold pads without corrosion — Fix clean with 99% IPA and reflow joints.
            2️⃣ Shorted capacitor on VBUS supply line 30 percent — Check measure resistance to ground in diode mode — Normal voltage drop 0.350V to 0.450V — Fix desolder shunted capacitor under shield.
            3️⃣ Sub-board charging IC fault 10 percent — Check measure battery FPC terminal voltage — Normal 3.7V to 4.2V — Fix replace sub-board logic unit.
            🔧 Step-by-Step
            Step 1 Visual Inspection — Inspect Type-C / Lightning port with bright flashlight and microscope for pin deformation.
            Step 2 Power Consumption Check — Connect inline USB tester to verify current draw (normal fast charge 1.2A - 2.0A).
            Step 3 Non-invasive check & reflow — Heat back glass to 100°C, open casing safely, and verify inter-board FPC seating.
            💡 Pro Tip Always perform non-invasive taps and pressure tests before cutting motherboard shields.
            ⚠️ Risks Do not exceed 100°C when heating back cover to prevent OLED/LCD backlight spot damage.
            🔗 Links https://techmate.app/schematics
        """.trimIndent()
    }
}
