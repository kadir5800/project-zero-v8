package com.zero.minidrama.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.zero.minidrama.data.BookResult
import com.zero.minidrama.data.HistoryItem
import com.zero.minidrama.ui.AppUiState
import com.zero.minidrama.ui.components.CategoryChipRow
import com.zero.minidrama.ui.components.PlatformChipRow
import com.zero.minidrama.ui.components.PosterCard
import com.zero.minidrama.ui.components.SectionTitle

private const val BROWSE_COLUMNS = 3

@Composable
fun HomeScreen(
    state: AppUiState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onPlatformChange: (String) -> Unit,
    onLangChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onBookClick: (BookResult) -> Unit,
    onPageChange: (Int) -> Unit,
    onResumeHistory: (HistoryItem) -> Unit
) {
    val continueItems = state.history.filter { it.platform == state.platform }.take(10)

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text("ZERO", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                Text("Mini drama izleyici", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = onQueryChange,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Dizi adi yazin...") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = onSearch,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Search, contentDescription = "Ara")
                    }
                }
                Spacer(Modifier.height(10.dp))
                PlatformChipRow(state.platform, state.lang, onPlatformChange, onLangChange)
                if (state.categories.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    CategoryChipRow(
                        categories = state.categories,
                        selectedId = state.categoryId,
                        onSelect = onCategoryChange
                    )
                }
            }
        }

        if (state.isSearching) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            return
        }

        if (state.results.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Icon(Icons.Default.PlayArrow, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Mini drama, telefonda", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                    Text("Ara, posterden dokun, izle.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
            }
            return
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(BROWSE_COLUMNS),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            if (continueItems.isNotEmpty()) {
                item(span = { GridItemSpan(BROWSE_COLUMNS) }) {
                    SectionTitle("Devam et")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(continueItems, key = { "${it.bookId}_${it.serial}" }) { item ->
                            ContinueCard(item, onResumeHistory)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    SectionTitle("Sonuclar")
                }
            }
            itemsIndexed(state.results, key = { index, book -> "${book.bookId}_$index" }) { _, book ->
                PosterCard(book, onBookClick)
            }
        }

        if (state.totalPages > 1) {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(onClick = { onPageChange(state.currentPage - 1) }, enabled = state.currentPage > 1) { Text("Onceki") }
                    Text("  ${state.currentPage} / ${state.totalPages}  ", fontWeight = FontWeight.SemiBold)
                    Button(onClick = { onPageChange(state.currentPage + 1) }, enabled = state.currentPage < state.totalPages) { Text("Sonraki") }
                }
            }
        }
    }
}

@Composable
private fun ContinueCard(item: HistoryItem, onClick: (HistoryItem) -> Unit) {
    Column(
        Modifier
            .width(100.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick(item) }
    ) {
        AsyncImage(
            model = item.pic,
            contentDescription = item.title,
            modifier = Modifier.height(140.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)),
            contentScale = ContentScale.Crop
        )
        Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
        Text("Bolum ${item.serial}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
    }
}
