package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.TechMateRepository
import com.example.model.GuideData
import com.example.service.AiService
import com.example.ui.screens.*
import com.example.ui.theme.TechMateTheme

class MainActivity : ComponentActivity() {

    private lateinit var repository: TechMateRepository
    private lateinit var aiService: AiService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        repository = TechMateRepository(applicationContext)
        aiService = AiService()

        setContent {
            val isDarkMode by repository.isDarkMode.collectAsState()
            val currentUser by repository.currentUser.collectAsState()
            val users by repository.users.collectAsState()

            val navController = rememberNavController()

            // State to pass pre-filled guide data from chat save request to Knowledge Base form
            var preFillGuideForKb by remember { mutableStateOf<GuideData?>(null) }

            TechMateTheme(darkTheme = isDarkMode) {
                NavHost(
                    navController = navController,
                    startDestination = "splash"
                ) {
                    // 1. Splash Screen
                    composable("splash") {
                        SplashScreen(
                            onNavigateNext = {
                                if (users.isEmpty()) {
                                    navController.navigate("register_admin") {
                                        popUpTo("splash") { inclusive = true }
                                    }
                                } else if (currentUser == null) {
                                    navController.navigate("login") {
                                        popUpTo("splash") { inclusive = true }
                                    }
                                } else if (currentUser?.role == "viewer") {
                                    navController.navigate("knowledge") {
                                        popUpTo("splash") { inclusive = true }
                                    }
                                } else {
                                    navController.navigate("chats") {
                                        popUpTo("splash") { inclusive = true }
                                    }
                                }
                            }
                        )
                    }

                    // 2. Auth Screens
                    composable("register_admin") {
                        RegisterAdminScreen(
                            repository = repository,
                            onRegisterSuccess = {
                                navController.navigate("chats") {
                                    popUpTo("register_admin") { inclusive = true }
                                }
                            }
                        )
                    }

                    composable("login") {
                        LoginScreen(
                            repository = repository,
                            onLoginSuccess = {
                                if (repository.currentUser.value?.role == "viewer") {
                                    navController.navigate("knowledge") {
                                        popUpTo("login") { inclusive = true }
                                    }
                                } else {
                                    navController.navigate("chats") {
                                        popUpTo("login") { inclusive = true }
                                    }
                                }
                            },
                            onGoToRegisterAdmin = {
                                navController.navigate("register_admin") {
                                    popUpTo("login") { inclusive = true }
                                }
                            }
                        )
                    }

                    // 3. Chats Screen
                    composable("chats") {
                        ChatsScreen(
                            repository = repository,
                            onChatSelected = { chatId ->
                                navController.navigate("chat/$chatId")
                            },
                            onNavigateToKnowledge = {
                                navController.navigate("knowledge")
                            },
                            onNavigateToProfile = {
                                navController.navigate("profile")
                            }
                        )
                    }

                    // 4. Chat Detail Screen
                    composable(
                        route = "chat/{chatId}",
                        arguments = listOf(navArgument("chatId") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val chatId = backStackEntry.arguments?.getString("chatId") ?: ""
                        ChatDetailScreen(
                            chatId = chatId,
                            repository = repository,
                            aiService = aiService,
                            onBack = { navController.popBackStack() },
                            onSaveToKbRequest = { guide ->
                                preFillGuideForKb = guide
                                navController.navigate("knowledge")
                            }
                        )
                    }

                    // 5. Knowledge Base Screen
                    composable("knowledge") {
                        KnowledgeBaseScreen(
                            repository = repository,
                            initialPreFillGuide = preFillGuideForKb,
                            onBack = {
                                preFillGuideForKb = null
                                if (navController.previousBackStackEntry != null) {
                                    navController.popBackStack()
                                } else {
                                    navController.navigate("chats")
                                }
                            }
                        )
                    }

                    // 6. Profile Screen
                    composable("profile") {
                        ProfileScreen(
                            repository = repository,
                            onNavigateToAdmin = {
                                navController.navigate("admin")
                            },
                            onLogout = {
                                navController.navigate("login") {
                                    popUpTo(0) { inclusive = true }
                                }
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
