package com.digital.sanghaworld.ui

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.digital.sanghaworld.billing.DonationTier
import com.digital.sanghaworld.billing.DonationViewModel

@Composable
fun SupportScreen(
    donationViewModel: DonationViewModel,
    modifier: Modifier = Modifier
) {
    val tiers by donationViewModel.tiers.collectAsState()
    val status by donationViewModel.status.collectAsState()
    val loading by donationViewModel.loading.collectAsState()
    val activity = LocalContext.current as Activity
    val isSuccess = status.orEmpty().startsWith("Thank you")
    val isSetupNote = status.orEmpty().startsWith("Set up these Play Console donation products")

    LaunchedEffect(Unit) {
        donationViewModel.reload()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = SanghaScreenPadding),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        ScreenIntro(
            subtitle = "Voluntary — never required. Choose a bundle; Google Play handles the rest."
        )
        Spacer(modifier = Modifier.height(24.dp))

        when {
            loading && tiers.isEmpty() -> {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp,
                    modifier = Modifier.padding(24.dp)
                )
            }
            !loading && tiers.isEmpty() -> {
                EmptyState(
                    title = "Donations unavailable",
                    body = "Products are not listed yet. You can still sit without donating."
                )
            }
            else -> {
                tiers.forEach { tier ->
                    DonationTierCard(
                        tier = tier,
                        activity = activity,
                        donationViewModel = donationViewModel
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }

        if (!status.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            StatusText(status.orEmpty(), error = !isSuccess && !isSetupNote)
            Spacer(modifier = Modifier.height(12.dp))
            if (isSuccess) {
                Text(
                    text = "Thank you",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
            } else if (!isSetupNote) {
                QuietButton(text = "Try again", onClick = { donationViewModel.reload() })
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Payments are processed by Google Play. Thank you for sitting, and for any support you choose to give.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun DonationTierCard(
    tier: DonationTier,
    activity: Activity,
    donationViewModel: DonationViewModel
) {
    val available = tier.isAvailable
    SanghaCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = tier.title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (available) tier.unitPriceLabel else "Not active yet",
                style = MaterialTheme.typography.displaySmall,
                color = if (available) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = when (tier.multiplier) {
                    1 -> "Base bundle"
                    10 -> "10× bundle"
                    100 -> "100× bundle"
                    else -> "Donation bundle"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            QuietButton(
                text = if (available) "Donate with Google Play" else "Set up in Play Console",
                emphasized = available,
                enabled = available,
                onClick = { donationViewModel.purchase(activity, tier.productId) }
            )
        }
    }
}
