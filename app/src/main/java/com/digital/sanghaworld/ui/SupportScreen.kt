package com.digital.sanghaworld.ui

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.sp
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
    val cardShape = RoundedCornerShape(20.dp)
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
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Donate",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "VOLUNTARY  -  NEVER REQUIRED",
            style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 2.sp, fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Choose a bundle below. Google Play lets the quantity go up to 10 on the checkout sheet, and these three tiers make larger donations easier to reach.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(28.dp))

        when {
            loading && tiers.isEmpty() -> {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            else -> {
                tiers.forEach { tier ->
                    DonationTierCard(
                        tier = tier,
                        activity = activity,
                        donationViewModel = donationViewModel,
                        cardShape = cardShape
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

        if (!status.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = status.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = if (isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(14.dp))
            if (isSuccess) {
                Text(
                    text = "Thank You",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
            } else if (!isSetupNote) {
                QuietButton(text = "Try again", onClick = { donationViewModel.reload() })
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
        Text(
            text = "Payments are processed by Google Play. Thank you for sitting and for any support you choose to give.",
            style = MaterialTheme.typography.bodyMedium,
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
    donationViewModel: DonationViewModel,
    cardShape: RoundedCornerShape
) {
    val available = tier.isAvailable
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, cardShape)
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f), cardShape)
            .padding(horizontal = 20.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = tier.title,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (available) tier.unitPriceLabel else "Not active yet",
            style = MaterialTheme.typography.displaySmall,
            color = if (available) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = when (tier.multiplier) {
                1 -> "Base bundle"
                10 -> "10x bundle"
                100 -> "100x bundle"
                else -> "Donation bundle"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Google Play quantity can still be increased up to 10 on checkout.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(18.dp))
        QuietButton(
            text = if (available) "Donate with Google Play" else "Set up in Play Console",
            emphasized = available,
            enabled = available,
            onClick = { donationViewModel.purchase(activity, tier.productId) }
        )
    }
}