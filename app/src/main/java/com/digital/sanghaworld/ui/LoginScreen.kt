package com.digital.sanghaworld.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.digital.sanghaworld.auth.AuthUiState

@Composable
fun LoginScreen(
    state: AuthUiState,
    onGoogle: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Sangha World",
            style = MaterialTheme.typography.displaySmall.copy(fontSize = 34.sp),
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
        Text(
            text = "SIT  ·  TOGETHER",
            style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 3.sp),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
            modifier = Modifier.padding(top = 8.dp, bottom = 36.dp)
        )
        Text(
            text = "Sign in to join meditation halls. Your account stays on our server; Google only proves it is you.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))
        if (state.loading) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        } else {
            QuietButton(
                text = "Continue with Google",
                onClick = onGoogle,
                emphasized = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (!state.error.isNullOrBlank()) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = state.error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }
    }
}
