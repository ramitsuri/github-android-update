package com.ramitsuri.githubandroidupdate.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Source
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ramitsuri.githubandroidupdate.updatechecker.lib.GitHubUpdateChecker
import com.ramitsuri.githubandroidupdate.updatechecker.lib.model.GitHubRelease
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.time.toKotlinInstant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val ownerState = rememberTextFieldState()
    val repoState = rememberTextFieldState()
    val tokenState = rememberTextFieldState()
    var date by remember { mutableLongStateOf(Instant.now().toEpochMilli()) }
    val dateState = rememberTextFieldState()
    LaunchedEffect(date) {
        val formatted = Instant.ofEpochMilli(date)
            .atZone(ZoneId.of("UTC"))
            .toLocalDate()
            .format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
        dateState.setTextAndPlaceCursorAtEnd(formatted)
    }
    var showDatePicker by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val checker = remember { GitHubUpdateChecker(context.applicationContext) }
    val coroutineScope = rememberCoroutineScope()
    var release by remember { mutableStateOf<GitHubRelease.Release?>(null) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("GitHub Update") },
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            OutlinedTextField(
                state = ownerState,
                label = { Text("GitHub Owner") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
            )
            OutlinedTextField(
                state = repoState,
                label = { Text("Repository Name") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Rounded.Source, contentDescription = null) },
            )
            OutlinedTextField(
                state = tokenState,
                label = { Text("Personal Access Token (Optional)") },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("ghp_...") },
                leadingIcon = { Icon(Icons.Rounded.Key, contentDescription = null) },
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .clickable { showDatePicker = true }
            ) {
                Text("Update since")
                Text(dateState.text.toString())
            }
            if (showDatePicker) {
                val datePickerState = rememberDatePickerState(
                    initialSelectedDateMillis = date
                )
                LaunchedEffect(datePickerState.selectedDateMillis) {
                    if (datePickerState.selectedDateMillis != null && datePickerState.selectedDateMillis != date) {
                        datePickerState.selectedDateMillis?.let {
                            date = it
                            showDatePicker = false
                        }
                    }
                }
                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = { },
                    dismissButton = { }
                ) {
                    DatePicker(state = datePickerState)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    coroutineScope.launch {
                        val releaseResult = checker.checkForUpdates(
                            owner = ownerState.text.toString(),
                            repo = repoState.text.toString(),
                            authToken = tokenState.text.toString(),
                            lastCheckedTimestamp = date.let {
                                Instant.ofEpochMilli(it).toKotlinInstant()
                            }
                        )
                        when (releaseResult) {
                            is GitHubRelease.Failure -> {
                                Toast.makeText(context, releaseResult.message, Toast.LENGTH_SHORT)
                                    .show()
                            }

                            is GitHubRelease.Release -> {
                                release = releaseResult
                            }
                        }
                    }
                }
            ) {
                Text("Check")
            }
            release?.let { release ->
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        coroutineScope.launch {
                            val error = checker.downloadAndInstall(release)
                            if (error != null) {
                                Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                ) {
                    Text("Install")
                }
            }
        }
    }
}
