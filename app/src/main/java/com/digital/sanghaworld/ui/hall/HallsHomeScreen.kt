package com.digital.sanghaworld.ui.hall

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.digital.sanghaworld.hall.HallCard
import com.digital.sanghaworld.hall.HallUiState
import com.digital.sanghaworld.ui.StatusText

private val Page = Color(0xFFF4F1E8)
private val OpenCard = Color(0xFFDDE3D7)
private val ScheduleCard = Color(0xFFE9E3D8)
private val Ink = Color(0xFF344334)
private val Muted = Color(0xFF6E7664)
private val Forest = Color(0xFF526650)
private val White = Color(0xFFFFFFFF)

@Composable
fun HallsHomeScreen(
    ui: HallUiState,
    onOpenHall: (HallCard) -> Unit,
    onOpenCommunity: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    onOpenMenu: () -> Unit = {},
    onCreateHall: () -> Unit = {},
    onBrowseHalls: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scheduledCount = (ui.discovery.catalog.ifEmpty {
        ui.discovery.sittingNow + ui.discovery.startingSoon + ui.discovery.myHalls
    }).distinctBy { it.hall.id }.size

    Column(
        modifier
            .fillMaxSize()
            .background(Page)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.Menu,
                contentDescription = "Menu",
                tint = Forest,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onOpenMenu)
                    .padding(8.dp)
            )
            Spacer(Modifier.size(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Meditation Hall",
                    color = Ink,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "Meditate together",
                    color = Muted,
                    fontSize = 14.sp
                )
            }
            Icon(
                Icons.Outlined.Person,
                contentDescription = "My Space",
                tint = Forest,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .border(1.dp, Color(0xFFC9CFC2), CircleShape)
                    .clickable(onClick = onOpenProfile)
                    .padding(8.dp)
            )
        }
        if (!ui.actionError.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            StatusText(ui.actionError, error = true)
        }
        Spacer(Modifier.height(18.dp))
        OpenHallCard(count = ui.communityCount, onEnter = onOpenCommunity)
        Spacer(Modifier.height(14.dp))
        ScheduleHallCard(
            scheduledCount = scheduledCount,
            onBrowse = onBrowseHalls,
            onCreate = onCreateHall
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun OpenHallCard(count: Int, onEnter: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(OpenCard)
            .padding(16.dp)
    ) {
        Text("Open Meditation Hall", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        Text(
            "A hall that is always open. Join anytime and sit alongside fellow meditators.",
            color = Muted,
            fontSize = 14.sp,
            lineHeight = 20.sp
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Groups, contentDescription = null, tint = Forest, modifier = Modifier.size(16.dp))
            Spacer(Modifier.size(6.dp))
            Text(
                if (count == 1) "1 meditating now" else "$count meditating now",
                color = Muted,
                fontSize = 13.sp
            )
        }
        Spacer(Modifier.height(14.dp))
        ForestButton("ENTER HALL", onEnter)
    }
}

@Composable
private fun ScheduleHallCard(scheduledCount: Int, onBrowse: () -> Unit, onCreate: () -> Unit) {
    val hallsLabel = if (scheduledCount == 1) "1 hall scheduled" else "$scheduledCount halls scheduled"
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(ScheduleCard)
            .padding(16.dp)
    ) {
        Text("Scheduled Halls", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        Text(
            "Join a scheduled group sitting or create a hall for others to join.",
            color = Muted,
            fontSize = 14.sp,
            lineHeight = 20.sp
        )
        Spacer(Modifier.height(14.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(White)
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Upcoming sittings", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text(hallsLabel, color = Muted, fontSize = 13.sp)
                }
                Icon(
                    Icons.Outlined.CalendarMonth,
                    contentDescription = null,
                    tint = Forest,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            ForestButton("VIEW SCHEDULED HALLS", onBrowse)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "+ CREATE A MEDITATION HALL",
            color = Forest,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.6.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(White)
                .clickable(onClick = onCreate)
                .padding(vertical = 14.dp)
        )
    }
}

@Composable
private fun ForestButton(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = White,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.8.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Forest)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp)
    )
}


