package com.ramitsuri.githubandroidupdate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.ramitsuri.githubandroidupdate.ui.navigation.MainKey
import com.ramitsuri.githubandroidupdate.ui.screens.MainScreen
import com.ramitsuri.githubandroidupdate.ui.theme.GitHubAndroidUpdateTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val backStack = rememberNavBackStack(MainKey)

            GitHubAndroidUpdateTheme {
                NavDisplay(
                    backStack = backStack,
                    onBack = {
                        if (backStack.size > 1) {
                            backStack.removeAt(backStack.size - 1)
                        } else {
                            finish()
                        }
                    },
                    entryProvider = entryProvider {
                        entry<MainKey> {
                            MainScreen()
                        }
                    }
                )
            }
        }
    }
}
