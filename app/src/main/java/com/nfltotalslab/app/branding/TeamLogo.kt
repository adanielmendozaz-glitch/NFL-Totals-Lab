package com.nfltotalslab.app.branding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext

@Composable
fun TeamBadge(team:String, size:Dp = 30.dp){
    val brand = teamBrand(team)
    val url = brand.logoUrl

    Surface(
        modifier = Modifier.size(size),
        shape = CircleShape,
        color = brand.secondary.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, brand.primary.copy(alpha = 0.35f))
    ){
        if(url.isNullOrBlank()){
            BadgeFallback(team)
        }else{
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(url)
                    .crossfade(true)
                    .build(),
                contentDescription = "$team logo",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(3.dp),
                contentScale = ContentScale.Fit,
                loading = { BadgeFallback(team) },
                error = { BadgeFallback(team) }
            )
        }
    }
}

@Composable
private fun BadgeFallback(team:String){
    val brand = teamBrand(team)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(CircleShape)
            .background(brand.primary.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ){
        Text(
            teamKey(team),
            color = brand.primary,
            fontWeight = FontWeight.Black,
            style = MaterialTheme.typography.labelSmall
        )
    }
}
