package com.digital.sanghaworld.ui.hall

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Page = Color(0xFFFCFCFC)
private val CardBeige = Color(0xFFE8E4DA)
private val HallPanel = Color(0xFFD5D6CB)
private val Seat = Color(0xFFC0C5B9)
private val SeatIcon = Color(0xFF6C7769)
private val InkOlive = Color(0xFF465447)
private val CountOlive = Color(0xFF526650)
private val Caption = Color(0xFF6E7668)
private val Chip = Color(0xFFE5E8DE)
private val ChipText = Color(0xFF6A7768)
private val DurationInk = Color(0xFF2C2C2C)
private val Forest = Color(0xFF526650)
private val White = Color(0xFFFFFFFF)

private val presets = listOf(30, 45, 60, 90, 120)

@Composable
fun CommunityRoomScreen(
    minutes: Int,
    sittingCount: Int,
    onMinutes: (Int) -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(minutes) {
        if (minutes !in presets) onMinutes(30)
    }
    val selected = if (minutes in presets) minutes else 30
    Column(
        modifier
            .fillMaxSize()
            .background(Page)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(CardBeige)
                .padding(14.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "OPEN MEDITATION HALL",
                    color = InkOlive,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.6.sp
                )
                Text(
                    "$sittingCount sitting",
                    color = CountOlive,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(Modifier.height(12.dp))
            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(HallPanel)
                    .padding(10.dp)
            ) {
                val columns = if (maxWidth >= 376.dp) 4 else 3
                Column {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        List(12) { it }.chunked(columns).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { _ ->
                                    SeatTile(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Fellow meditators are sitting",
                        modifier = Modifier.fillMaxWidth(),
                        color = Caption,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(White)
                    .padding(14.dp)
            ) {
                Text(
                    "Your sitting duration",
                    color = DurationInk,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    presets.forEach { choice ->
                        DurationChip(
                            label = "$choice min",
                            selected = selected == choice,
                            onClick = { onMinutes(choice) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    "SIT NOW",
                    color = White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.2.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Forest)
                        .clickable(onClick = onStart)
                        .padding(vertical = 14.dp)
                )
            }
        }
    }
}

@Composable
private fun SeatTile(modifier: Modifier = Modifier) {
    Column(
        modifier
            .height(58.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Seat),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Outlined.Person,
            contentDescription = null,
            tint = SeatIcon
        )
    }
}

@Composable
private fun DurationChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Text(
        label,
        color = if (selected) InkOlive else ChipText,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Color(0xFFB7BEB0) else Chip)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
    )
}
