package com.example.tiresairpressuremanagement.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private const val TAG = "SettingsScreen"

@Composable
fun SettingsScreen(
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // כפתור חזרה
        Button(
            onClick = {
                android.util.Log.d(TAG, "Back button clicked")
                onBack()
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Text("חזור")
        }
        
        // כותרת
        Text(
            text = "הגדרות",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        // הגדרות נוספות יתווספו כאן בהמשך
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "גרסת האפליקציה",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "1.0.0",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
} 