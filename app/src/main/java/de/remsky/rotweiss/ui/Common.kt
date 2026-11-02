@file:OptIn(ExperimentalMaterial3Api::class)

package de.remsky.rotweiss.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import de.remsky.rotweiss.PlayerActivity
import de.remsky.rotweiss.data.Category
import de.remsky.rotweiss.data.EpgEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val FcRed = Color(0xFFE3141B)

val AppColors = darkColorScheme(
    primary = FcRed,
    onPrimary = Color.White,
    secondary = Color.White,
    background = Color(0xFF140C0D),
    surface = Color(0xFF1E1416),
    surfaceVariant = Color(0xFF2B1C1F),
    surfaceContainer = Color(0xFF1E1416),
    secondaryContainer = Color(0xFF5A1216),
    onSecondaryContainer = Color.White,
)

val Gold = Color(0xFFFFC145)

fun Context.openPlayer() {
    startActivity(Intent(this, PlayerActivity::class.java))
}

private val hm = SimpleDateFormat("HH:mm", Locale.GERMANY)
private val dayFmt = SimpleDateFormat("EEE dd.MM.", Locale.GERMANY)

fun clock(sec: Long): String = hm.format(Date(sec * 1000))
fun day(sec: Long): String = dayFmt.format(Date(sec * 1000))
fun timeRange(e: EpgEntry) = "${clock(e.start)}–${clock(e.end)}"

fun epgProgress(e: EpgEntry): Float {
    val now = System.currentTimeMillis() / 1000
    if (e.end <= e.start) return 0f
    return ((now - e.start).toFloat() / (e.end - e.start)).coerceIn(0f, 1f)
}

// ---------------------------------------------------------------- Suche, Kategorien, Sortierung

@Composable
fun SearchField(value: String, onChange: (String) -> Unit, hint: String) {
    OutlinedTextField(
        value, onChange,
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        placeholder = { Text(hint) },
        leadingIcon = { Icon(Icons.Default.Search, null) },
        trailingIcon = {
            if (value.isNotEmpty()) IconButton({ onChange("") }) { Icon(Icons.Default.Close, "Leeren") }
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
    )
}

@Composable
fun CategoryChips(categories: List<Category>, selected: String?, onSelect: (String?) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilterChip(selected == null, { onSelect(null) }, { Text("Alle") })
        }
        items(categories, key = { it.id }) { cat ->
            FilterChip(selected == cat.id, { onSelect(cat.id) }, { Text(cat.name, maxLines = 1) })
        }
    }
}

@Composable
fun SortButton(current: SortMode, onChange: (SortMode) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton({ open = true }) { Icon(Icons.Default.Sort, "Sortieren") }
        DropdownMenu(open, { open = false }) {
            SortMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = {
                        Text(mode.label, fontWeight = if (mode == current) FontWeight.Bold else FontWeight.Normal)
                    },
                    onClick = { onChange(mode); open = false },
                )
            }
        }
    }
}

@Composable
fun TitleBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        navigationIcon = {
            if (onBack != null) IconButton(onBack) { Icon(Icons.Default.ArrowBack, "Zurück") }
        },
        title = {
            Column {
                Text(title, fontWeight = FontWeight.Bold)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

// ---------------------------------------------------------------- Kacheln

@Composable
fun PosterCard(
    title: String,
    image: String?,
    rating: String,
    favorite: Boolean,
    onClick: () -> Unit,
) {
    Column(Modifier.clickable(onClick = onClick).padding(4.dp)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (image != null) {
                AsyncImage(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Text(
                    title.take(24), color = Color.Gray, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(8.dp), fontSize = 13.sp,
                )
            }
            if (rating.isNotEmpty()) {
                Row(
                    Modifier.align(Alignment.TopStart).padding(5.dp)
                        .background(Color(0xCC000000), RoundedCornerShape(6.dp)).padding(horizontal = 5.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Star, null, Modifier.size(11.dp), tint = Gold)
                    Spacer(Modifier.width(2.dp))
                    Text(rating, color = Color.White, fontSize = 11.sp)
                }
            }
            if (favorite) {
                Icon(
                    Icons.Default.Star, "Favorit",
                    Modifier.align(Alignment.TopEnd).padding(5.dp).size(18.dp), tint = Gold,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, lineHeight = 16.sp)
    }
}

@Composable
fun CenterText(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, color = Color.Gray, textAlign = TextAlign.Center)
    }
}

@Composable
fun CenterLoading(text: String = "Wird geladen …") {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text(text, color = Color.Gray)
        }
    }
}

// ---------------------------------------------------------------- PIN

@Composable
fun PinDialog(
    title: String,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Boolean,   // true = Dialog schließen
) {
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    pin, { if (it.length <= 8 && it.all(Char::isDigit)) { pin = it; wrong = false } },
                    singleLine = true,
                    label = { Text("PIN (4–8 Ziffern)") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    isError = wrong,
                )
                if (wrong) Text("Falsche PIN", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }
        },
        confirmButton = {
            TextButton({ if (onSubmit(pin)) onDismiss() else wrong = true }, enabled = pin.length >= 4) {
                Text("OK")
            }
        },
        dismissButton = { TextButton(onDismiss) { Text("Abbrechen") } },
    )
}
