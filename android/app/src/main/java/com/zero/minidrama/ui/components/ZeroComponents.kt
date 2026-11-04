package com.zero.minidrama.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.CircularProgressIndicator
import coil.compose.SubcomposeAsyncImage
import com.zero.minidrama.core.ImageLoading
import com.zero.minidrama.data.BookResult
import com.zero.minidrama.data.CategoryItem

@Composable
fun ZeroBrandHeader(subtitle: String? = null) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            "ZERO",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onBackground
        )
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
            )
        }
    }
}

@Composable
fun PlatformChipRow(
    platform: String,
    lang: String,
    onPlatformChange: (String) -> Unit,
    onLangChange: (String) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(
            "reelshort" to "ReelShort",
            "dramabox" to "DramaBox",
            "netshort" to "NetShort",
            "dramawave" to "DramaWave"
        ).forEach { (id, label) ->
            FilterChip(
                selected = platform == id,
                onClick = { onPlatformChange(id) },
                label = { Text(label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
        FilterChip(
            selected = lang == "tr",
            onClick = { onLangChange("tr") },
            label = { Text("TR") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primary,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
            )
        )
        FilterChip(
            selected = lang == "en",
            onClick = { onLangChange("en") },
            label = { Text("EN") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primary,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
            )
        )
    }
}

@Composable
fun CategoryChipRow(
    categories: List<CategoryItem>,
    selectedId: String,
    onSelect: (String) -> Unit
) {
    if (categories.isEmpty()) return
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        categories.forEach { cat ->
            FilterChip(
                selected = selectedId == cat.id,
                onClick = { onSelect(cat.id) },
                label = { Text(cat.name) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondary,
                    selectedLabelColor = MaterialTheme.colorScheme.onSecondary
                )
            )
        }
    }
}

@Composable
fun PosterCard(book: BookResult, onClick: (BookResult) -> Unit, modifier: Modifier = Modifier) {
    val poster = ImageLoading.normalizePosterUrl(book.pic)
    Surface(
        modifier = modifier
            .aspectRatio(0.72f)
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick(book) },
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 2.dp
    ) {
        Box(Modifier.fillMaxSize()) {
            if (poster.isNotBlank()) {
                SubcomposeAsyncImage(
                    model = poster,
                    contentDescription = book.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    loading = {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    error = {
                        PosterFallback(book.title)
                    }
                )
            } else {
                PosterFallback(book.title)
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.55f to Color.Transparent,
                            1f to Color(0xE6000000)
                        )
                    )
            )
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(28.dp)
                    .background(Color(0xCCFF3B5C), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
            Column(Modifier.align(Alignment.BottomStart).padding(8.dp)) {
                Text(
                    book.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge
                )
                if (book.chapterCount > 0) {
                    Text(
                        "${book.chapterCount} bolum",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFCCCCCC)
                    )
                }
            }
        }
    }
}

@Composable
private fun PosterFallback(title: String) {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF2A2A32), Color(0xFF121218))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            title.take(24),
            color = Color.White.copy(alpha = 0.85f),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(8.dp),
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
fun SectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        modifier = modifier.padding(vertical = 4.dp),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    )
}
