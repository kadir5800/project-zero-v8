package com.zero.minidrama.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Restore
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zero.minidrama.data.HistoryBackup
import com.zero.minidrama.ui.components.PlatformChipRow

@Composable
fun SettingsScreen(
    platform: String,
    lang: String,
    onPlatformChange: (String) -> Unit,
    onLangChange: (String) -> Unit,
    onRestoreHistory: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text("Ayarlar", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                Text("Varsayilan kaynak ve dil", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
            }
        }

        Column(Modifier.padding(16.dp)) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Kaynak ve dil", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    PlatformChipRow(platform, lang, onPlatformChange, onLangChange)
                }
            }

            Spacer(Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Izleme gecmisi", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Her izlemede otomatik yedek: ${HistoryBackup.publicBackupHint()}",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onRestoreHistory, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Restore, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Yedekten geri yukle")
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
            ) {
                RowWithIcon(
                    icon = { Icon(Icons.Default.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    title = "Sunucu gerekmez",
                    body = "Uygulama dogrudan ReelShort, DramaBox ve NetShort kaynaklarina baglanir. PC acik tutmaniza gerek yok."
                )
            }
        }
    }
}

@Composable
private fun RowWithIcon(
    icon: @Composable () -> Unit,
    title: String,
    body: String
) {
    Column(Modifier.padding(16.dp)) {
        icon()
        Spacer(Modifier.height(8.dp))
        Text(title, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(body, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
    }
}
