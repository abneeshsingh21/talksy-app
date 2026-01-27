package com.jengachat.ui.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.jengachat.data.model.CallType
import com.jengachat.ui.auth.*
import com.jengachat.ui.call.*
import com.jengachat.ui.chat.*
import com.jengachat.ui.profile.*

sealed class Screen(val route: String) {
    object Welcome : Screen("welcome")
    object Login : Screen("login")
    object SignUp : Screen("signup")
    object ForgotPassword : Screen("forgot_password")
    
    object ChatList : Screen("chat_list")
    object Conversation : Screen("conversation/{chatId}") {
        fun createRoute(chatId: String) = "conversation/$chatId"
    }
    object NewChat : Screen("new_chat")
    object NewGroup : Screen("new_group")
    object GroupInfo : Screen("group_info/{chatId}") {
        fun createRoute(chatId: String) = "group_info/$chatId"
    }
    
    object VoiceCall : Screen("voice_call/{callId}?isIncoming={isIncoming}") {
        fun createRoute(callId: String, isIncoming: Boolean = false) = 
            "voice_call/$callId?isIncoming=$isIncoming"
    }
    object VideoCall : Screen("video_call/{callId}?isIncoming={isIncoming}") {
        fun createRoute(callId: String, isIncoming: Boolean = false) = 
            "video_call/$callId?isIncoming=$isIncoming"
    }
    object GroupCall : Screen("group_call/{callId}?isIncoming={isIncoming}") {
        fun createRoute(callId: String, isIncoming: Boolean = false) = 
            "group_call/$callId?isIncoming=$isIncoming"
    }
    object CallHistory : Screen("call_history")
    
    object Profile : Screen("profile")
    object EditProfile : Screen("edit_profile")
    object Settings : Screen("settings")
    object About : Screen("about")
    object Help : Screen("help")
}

@Composable
fun AppNavigation(
    navController: NavHostController,
    isLoggedIn: Boolean
) {
    NavHost(
        navController = navController,
        startDestination = if (isLoggedIn) Screen.ChatList.route else Screen.Welcome.route
    ) {
        // Auth screens
        composable(Screen.Welcome.route) {
            WelcomeScreen(
                onLoginClick = { navController.navigate(Screen.Login.route) },
                onSignUpClick = { navController.navigate(Screen.SignUp.route) }
            )
        }
        
        composable(Screen.Login.route) {
            val authViewModel: AuthViewModel = hiltViewModel()
            LoginScreen(
                viewModel = authViewModel,
                onBackClick = { navController.popBackStack() },
                onLoginSuccess = {
                    navController.navigate(Screen.ChatList.route) {
                        popUpTo(Screen.Welcome.route) { inclusive = true }
                    }
                },
                onForgotPasswordClick = { navController.navigate(Screen.ForgotPassword.route) },
                onSignUpClick = { navController.navigate(Screen.SignUp.route) }
            )
        }
        
        composable(Screen.SignUp.route) {
            val authViewModel: AuthViewModel = hiltViewModel()
            SignUpScreen(
                viewModel = authViewModel,
                onBackClick = { navController.popBackStack() },
                onSignUpSuccess = {
                    navController.navigate(Screen.ChatList.route) {
                        popUpTo(Screen.Welcome.route) { inclusive = true }
                    }
                },
                onLoginClick = { navController.navigate(Screen.Login.route) }
            )
        }
        
        composable(Screen.ForgotPassword.route) {
            val authViewModel: AuthViewModel = hiltViewModel()
            ForgotPasswordScreen(
                viewModel = authViewModel,
                onBackClick = { navController.popBackStack() }
            )
        }
        
        // Chat screens
        composable(Screen.ChatList.route) {
            ChatListScreen(
                onChatClick = { chatId ->
                    navController.navigate(Screen.Conversation.createRoute(chatId))
                },
                onNewChatClick = { navController.navigate(Screen.NewChat.route) },
                onProfileClick = { navController.navigate(Screen.Profile.route) },
                onCallHistoryClick = { navController.navigate(Screen.CallHistory.route) }
            )
        }
        
        composable(
            route = Screen.Conversation.route,
            arguments = listOf(navArgument("chatId") { type = NavType.StringType })
        ) { backStackEntry ->
            val chatId = backStackEntry.arguments?.getString("chatId") ?: return@composable
            val callViewModel: CallViewModel = hiltViewModel()
            
            ConversationScreen(
                chatId = chatId,
                onBackClick = { navController.popBackStack() },
                onGroupInfoClick = { navController.navigate(Screen.GroupInfo.createRoute(chatId)) },
                onVoiceCallClick = { conversationId ->
                    // Initiate voice call via API
                    callViewModel.initiateCallForConversation(
                        conversationId = conversationId,
                        callType = CallType.VOICE
                    ) { callId ->
                        navController.navigate(Screen.VoiceCall.createRoute(callId))
                    }
                },
                onVideoCallClick = { conversationId ->
                    // Initiate video call via API
                    callViewModel.initiateCallForConversation(
                        conversationId = conversationId,
                        callType = CallType.VIDEO
                    ) { callId ->
                        navController.navigate(Screen.VideoCall.createRoute(callId))
                    }
                }
            )
        }
        
        composable(Screen.NewChat.route) {
            NewChatScreen(
                onBackClick = { navController.popBackStack() },
                onChatCreated = { chatId ->
                    navController.navigate(Screen.Conversation.createRoute(chatId)) {
                        popUpTo(Screen.ChatList.route)
                    }
                },
                onNewGroupClick = { navController.navigate(Screen.NewGroup.route) }
            )
        }
        
        composable(Screen.NewGroup.route) {
            NewGroupScreen(
                onBackClick = { navController.popBackStack() },
                onGroupCreated = { chatId ->
                    navController.navigate(Screen.Conversation.createRoute(chatId)) {
                        popUpTo(Screen.ChatList.route)
                    }
                }
            )
        }
        
        composable(
            route = Screen.GroupInfo.route,
            arguments = listOf(navArgument("chatId") { type = NavType.StringType })
        ) { backStackEntry ->
            val chatId = backStackEntry.arguments?.getString("chatId") ?: return@composable
            GroupInfoScreen(
                chatId = chatId,
                onBackClick = { navController.popBackStack() },
                onLeaveGroup = {
                    navController.navigate(Screen.ChatList.route) {
                        popUpTo(Screen.ChatList.route) { inclusive = true }
                    }
                }
            )
        }
        
        // Call screens
        composable(
            route = Screen.VoiceCall.route,
            arguments = listOf(
                navArgument("callId") { type = NavType.StringType },
                navArgument("isIncoming") { type = NavType.BoolType; defaultValue = false }
            )
        ) { backStackEntry ->
            val callId = backStackEntry.arguments?.getString("callId") ?: return@composable
            val isIncoming = backStackEntry.arguments?.getBoolean("isIncoming") ?: false
            VoiceCallScreen(
                callId = callId,
                isIncoming = isIncoming,
                onEndCall = { navController.popBackStack() }
            )
        }
        
        composable(
            route = Screen.VideoCall.route,
            arguments = listOf(
                navArgument("callId") { type = NavType.StringType },
                navArgument("isIncoming") { type = NavType.BoolType; defaultValue = false }
            )
        ) { backStackEntry ->
            val callId = backStackEntry.arguments?.getString("callId") ?: return@composable
            val isIncoming = backStackEntry.arguments?.getBoolean("isIncoming") ?: false
            VideoCallScreen(
                callId = callId,
                isIncoming = isIncoming,
                onEndCall = { navController.popBackStack() }
            )
        }
        
        composable(
            route = Screen.GroupCall.route,
            arguments = listOf(
                navArgument("callId") { type = NavType.StringType },
                navArgument("isIncoming") { type = NavType.BoolType; defaultValue = false }
            )
        ) { backStackEntry ->
            val callId = backStackEntry.arguments?.getString("callId") ?: return@composable
            val isIncoming = backStackEntry.arguments?.getBoolean("isIncoming") ?: false
            GroupCallScreen(
                callId = callId,
                isIncoming = isIncoming,
                onEndCall = { navController.popBackStack() }
            )
        }
        
        composable(Screen.CallHistory.route) {
            CallHistoryScreen(
                onBackClick = { navController.popBackStack() },
                onCallClick = { receiverId, callType ->
                    if (callType == CallType.VIDEO) {
                        navController.navigate(Screen.VideoCall.createRoute("temp_call_id"))
                    } else {
                        navController.navigate(Screen.VoiceCall.createRoute("temp_call_id"))
                    }
                }
            )
        }
        
        // Profile screens
        composable(Screen.Profile.route) {
            ProfileScreen(
                onBackClick = { navController.popBackStack() },
                onEditProfileClick = { navController.navigate(Screen.EditProfile.route) },
                onSettingsClick = { navController.navigate(Screen.Settings.route) },
                onAboutClick = { navController.navigate(Screen.About.route) },
                onHelpClick = { navController.navigate(Screen.Help.route) },
                onSignOut = {
                    navController.navigate(Screen.Welcome.route) {
                        popUpTo(Screen.ChatList.route) { inclusive = true }
                    }
                }
            )
        }
        
        composable(Screen.EditProfile.route) {
            EditProfileScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
        
        composable(Screen.Settings.route) {
            SettingsScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
        
        composable(Screen.About.route) {
            AboutScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
        
        composable(Screen.Help.route) {
            HelpScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}
