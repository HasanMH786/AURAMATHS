package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.ai.GeminiMathService
import com.example.data.repository.NotebookRepository
import com.example.security.configureAppCheck
import com.example.ui.auth.AuthScreen
import com.example.ui.theme.AuraMathTheme
import com.example.ui.viewmodel.AuraMathViewModel
import com.example.ui.workspace.AuraWorkspaceScreen
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure App Check with launch intent before rendering UI or creating ViewModels.
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            var isDarkTheme by remember { mutableStateOf(true) }
            AuraMathTheme(darkTheme = isDarkTheme) {
                AppNavigation(
                    onThemeChanged = { isDarkTheme = it }
                )
            }
        }
    }
}

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

@Composable
fun AppNavigation(
    onThemeChanged: (Boolean) -> Unit,
    auth: FirebaseAuth = Firebase.auth
) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        val uid = user.uid
        key(uid) {
            val viewModel: AuraMathViewModel = viewModel(
                key = uid,
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY]) {
                            "APPLICATION_KEY missing from CreationExtras"
                        }
                        val databaseId = app.getString(R.string.firestore_database_id)
                        val db = FirebaseFirestore.getInstance(databaseId)
                        val repository = NotebookRepository(db)
                        val aiService = GeminiMathService()
                        AuraMathViewModel(repository, aiService, uid)
                    }
                }
            )
            AuraWorkspaceScreen(
                viewModel = viewModel,
                onThemeChanged = onThemeChanged
            )
        }
    }
}
