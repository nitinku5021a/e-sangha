package com.digital.sanghaworld.ui.hall

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.digital.sanghaworld.hall.HallSeatLayout
import com.digital.sanghaworld.hall.MeditationSeat

@Composable
fun SeatedMeditator(
    seat: MeditationSeat,
    modifier: Modifier = Modifier
) {
    val w = (118f * seat.scale).dp
    val h = (128f * seat.scale).dp
    Box(modifier.size(w, h), contentAlignment = Alignment.BottomCenter) {
        Image(
            painter = painterResource(HallSeatLayout.emptyCushion),
            contentDescription = if (seat.occupied) null else "Empty cushion",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .height(h * 0.46f)
                .align(Alignment.BottomCenter)
        )
        if (seat.occupied && seat.look != null) {
            Image(
                painter = painterResource(seat.look.drawableRes),
                contentDescription = "Meditator",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth(0.86f)
                    .height(h * 0.78f)
                    .align(Alignment.BottomCenter)
                    .offset(y = (-h * 0.16f))
            )
        }
        val name = seat.label?.takeIf { it.isNotBlank() }
        if (seat.occupied && name != null) {
            Text(
                name,
                color = Color(0xFF2C261E),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = 10.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFF7F1E6))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}
