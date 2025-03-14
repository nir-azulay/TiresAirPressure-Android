package com.example.tiresairpressuremanagement.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tiresairpressuremanagement.R
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onTimeout: () -> Unit,
    connectionInProgress: Boolean
) {
    // Dark blue background color used in the app
    val backgroundColor = Color(0xFF1E2530)
    
    // Effect to handle timeout
    LaunchedEffect(key1 = true) {
        // Wait for 3 seconds before calling onTimeout
        delay(3000)
        onTimeout()
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        // Background logo image that fills the entire screen
        Image(
            painter = painterResource(id = R.drawable.splash_logo),
            contentDescription = stringResource(R.string.app_logo),
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.7f), // Increased opacity significantly
            contentScale = ContentScale.Fit // Changed to Fit for better proportions
        )
        
        // Dark overlay to ensure text is readable
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            backgroundColor.copy(alpha = 0.2f), // Reduced overlay opacity
                            backgroundColor.copy(alpha = 0.5f)  // Reduced overlay opacity
                        ),
                        radius = 1200f
                    )
                )
        )
        
        // Content column with app name and connection status
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .zIndex(2f) // Ensure content is above the background
                .padding(bottom = 100.dp) // Add some padding at the bottom
        ) {
            // App name
            Text(
                text = stringResource(R.string.app_name),
                color = Color.White,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            // Show progress indicator if connection is in progress
            if (connectionInProgress) {
                CircularProgressIndicator(
                    modifier = Modifier.size(56.dp),
                    color = Color(0xFF3F51B5) // Blue color used in the app
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    text = stringResource(R.string.connecting),
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
} 