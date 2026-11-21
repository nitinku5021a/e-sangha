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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Page = Color(0xFFF4F1E8)
private val CardBeige = Color(0xFFE9E3D8)
private val HallPanel = Color(0xFFDDE3D7)
private val Seat = Color(0xFFC9D1C2)
private val SeatIcon = Color(0xFF526650)
private val InkOlive = Color(0xFF344334)
private val CountOlive = Color(0xFF526650)
private val Caption = Color(0xFF6E7664)
private val Chip = Color(0xFFE5E8DE)
private val ChipText = Color(0xFF6E7664)
private val DurationInk = Color(0xFF344334)
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
    var customOpen by remember { mutableStateOf(minutes !in presets) }
    var customText by remember { mutableStateOf(if (minutes !in presets) minutes.toString() else "") }
    val customMinutes = customText.toIntOrNull()?.takeIf { it in 1..999 }
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
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    presets.forEach { choice ->
                        DurationChip(
                            label = "${choice}m",
                            selected = !customOpen && minutes == choice,
                            onClick = {
                                customOpen = false
                                onMinutes(choice)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    DurationChip(
                        label = if (customOpen && customMinutes != null) "${customMinutes}m" else "+",
                        selected = customOpen,
                        onClick = { customOpen = true },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (customOpen) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextField(
                            value = customText,
                            onValueChange = { raw ->
                                customText = raw.filter { it.isDigit() }.take(3)
                                customText.toIntOrNull()?.takeIf { it in 1..999 }?.let(onMinutes)
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            placeholder = { Text("1–999", color = ChipText, fontSize = 14.sp) },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Chip,
                                unfocusedContainerColor = Chip,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                cursorColor = Forest,
                                focusedTextColor = InkOlive,
                                unfocusedTextColor = InkOlive
                            ),
                            modifier = Modifier.weight(1f).height(52.dp)
                        )
                        Text("m", color = InkOlive, fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp))
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
        softWrap = false,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Color(0xFFB7BEB0) else Chip)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
    )
}
