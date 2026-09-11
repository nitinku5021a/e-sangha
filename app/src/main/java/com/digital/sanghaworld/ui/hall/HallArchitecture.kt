package com.digital.sanghaworld.ui.hall

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.digital.sanghaworld.R

@Composable
fun HallArchitecture(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.hall_interior),
        contentDescription = null,
        modifier = modifier.fillMaxSize(),
        contentScale = ContentScale.Crop,
        alignment = Alignment.Center
    )
}
