package com.digital.sanghaworld.ui.hall

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.digital.sanghaworld.hall.HallCard
import com.digital.sanghaworld.hall.ScheduleType
import com.digital.sanghaworld.hall.camelCaseHallName
import com.digital.sanghaworld.ui.SanghaField
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val Page = Color(0xFFF4F1E8)
private val OpenCard = Color(0xFFDDE3D7)
private val ScheduleCard = Color(0xFFE9E3D8)
private val Ink = Color(0xFF344334)
private val Muted = Color(0xFF6E7664)
private val Forest = Color(0xFF526650)
private val White = Color(0xFFFFFFFF)

@Composable
fun HallDirectoryScreen(
    halls: List<HallCard>,
    searching: Boolean = false,
    onOpen: (HallCard) -> Unit,
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(searching) {
        if (!searching) {
            query = ""
        } else {
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }
    val unique = remember(halls) {
        halls.distinctBy { it.hall.id }
    }
    val q = query.trim()
    fun matches(card: HallCard) = q.isBlank() ||
        card.hall.name.contains(q, ignoreCase = true) ||
        camelCaseHallName(card.hall.name).contains(q, ignoreCase = true) ||
        card.hall.description.contains(q, ignoreCase = true)
    val mine = unique.filter { it.joined && matches(it) }
    val explore = unique.filter { !it.joined && matches(it) }
    Column(
        modifier
            .fillMaxSize()
            .background(Page)
    ) {
        if (searching) {
            SanghaField(
                value = query,
                onValueChange = { query = it },
                label = "Search halls",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                focusRequester = focusRequester
            )
        }
        if (unique.isEmpty()) {
            Text(
                "No halls yet.",
                color = Muted,
                fontSize = 15.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                if (mine.isEmpty() && explore.isEmpty()) {
                    Text("No halls match that search.", color = Muted, fontSize = 15.sp)
                    Spacer(Modifier.height(14.dp))
                }
                FittingHallSections(
                    mine = mine,
                    explore = explore,
                    onOpen = onOpen,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun FittingHallSections(
    mine: List<HallCard>,
    explore: List<HallCard>,
    onOpen: (HallCard) -> Unit,
    modifier: Modifier = Modifier
) {
    val gap = 14.dp
    SubcomposeLayout(modifier.fillMaxWidth()) { constraints ->
        val width = constraints.maxWidth
        val available = constraints.maxHeight
        val gapPx = gap.roundToPx()
        val half = (available / 2).coerceAtLeast(0)
        val loose = Constraints(maxWidth = width)

        val myNatural = subcompose("my-natural") {
            HallSectionCard(
                title = "My Halls",
                background = OpenCard,
                empty = "You have not joined a hall yet.",
                halls = mine,
                onOpen = onOpen,
                scroll = false
            )
        }.first().measure(loose)
        val myHeight = myNatural.height.coerceAtMost(half)
        val myCard = if (myNatural.height <= half) {
            myNatural
        } else {
            subcompose("my-capped") {
                HallSectionCard(
                    title = "My Halls",
                    background = OpenCard,
                    empty = "You have not joined a hall yet.",
                    halls = mine,
                    onOpen = onOpen,
                    scroll = true,
                    modifier = Modifier.fillMaxHeight()
                )
            }.first().measure(Constraints(maxWidth = width, minHeight = myHeight, maxHeight = myHeight))
        }

        val exploreMax = (available - myHeight - gapPx).coerceAtLeast(0)
        val exploreNatural = subcompose("explore-natural") {
            HallSectionCard(
                title = "Explore Halls",
                background = ScheduleCard,
                empty = "No halls to join right now.",
                halls = explore,
                onOpen = onOpen,
                scroll = false
            )
        }.first().measure(loose)
        val exploreHeight = exploreNatural.height.coerceAtMost(exploreMax)
        val exploreCard = if (exploreNatural.height <= exploreMax) {
            exploreNatural
        } else {
            subcompose("explore-capped") {
                HallSectionCard(
                    title = "Explore Halls",
                    background = ScheduleCard,
                    empty = "No halls to join right now.",
                    halls = explore,
                    onOpen = onOpen,
                    scroll = true,
                    modifier = Modifier.fillMaxHeight()
                )
            }.first().measure(
                Constraints(maxWidth = width, minHeight = exploreHeight, maxHeight = exploreHeight)
            )
        }

        layout(width, available) {
            myCard.place(0, 0)
            exploreCard.place(0, myHeight + gapPx)
        }
    }
}

@Composable
private fun HallSectionCard(
    title: String,
    background: Color,
    empty: String,
    halls: List<HallCard>,
    onOpen: (HallCard) -> Unit,
    scroll: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(background)
            .padding(16.dp)
    ) {
        Text(title, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(12.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .then(if (scroll) Modifier.weight(1f).verticalScroll(rememberScrollState()) else Modifier),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (halls.isEmpty()) {
                Text(empty, color = Muted, fontSize = 14.sp, lineHeight = 20.sp)
            } else {
                halls.forEach { card ->
                    DirectoryCard(card, onClick = { onOpen(card) })
                }
            }
        }
    }
}

@Composable
private fun DirectoryCard(card: HallCard, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    val localStart = Instant.ofEpochMilli(card.nextStartMillis).atZone(ZoneId.systemDefault())
    val whenLabel = localStart.format(DateTimeFormatter.ofPattern("EEE")) + " " +
        localStart.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
    val people = if (card.participantCount == 1) "1 sitting" else "${card.participantCount} sitting"
    val detail = "${scheduleName(card)} · $people · $whenLabel"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(White)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            camelCaseHallName(card.hall.name),
            color = Ink,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 18.sp
        )
        Text(
            detail,
            color = Forest,
            fontSize = 12.sp,
            lineHeight = 15.sp,
            maxLines = 1,
            softWrap = false
        )
    }
}

private fun scheduleName(card: HallCard): String = when (card.schedule.scheduleType) {
    ScheduleType.ONCE -> "Once"
    ScheduleType.DAILY -> "Daily"
    ScheduleType.WEEKLY -> "Weekly"
    ScheduleType.WEEKDAYS -> "Weekdays"
    ScheduleType.CUSTOM -> "Custom"
}
