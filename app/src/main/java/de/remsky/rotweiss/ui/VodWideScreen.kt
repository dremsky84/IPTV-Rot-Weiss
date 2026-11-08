@file:OptIn(ExperimentalMaterial3Api::class)

package de.remsky.rotweiss.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.remsky.rotweiss.data.Category

/**
 * Breitansicht für Filme und Serien am Fernseher/Tablet:
 * links Kategorien mit Anzahl, rechts das Poster-Raster.
 */
@Composable
fun VodWideLayout(
    title: String,
    subtitle: String?,
    searchHint: String,
    query: String,
    onQuery: (String) -> Unit,
    categories: List<Category>,
    counts: Map<String, Int>,
    selectedCategory: String?,
    onCategory: (String?) -> Unit,
    sort: SortMode,
    onSort: (SortMode) -> Unit,
    loading: Boolean,
    loadingText: String,
    isEmpty: Boolean,
    emptyText: String,
    grid: LazyGridScope.() -> Unit,
) {
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.weight(0.26f).fillMaxHeight()) {
            Spacer(Modifier.height(8.dp))
            SearchField(query, onQuery, searchHint)
            Spacer(Modifier.height(6.dp))
            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                item {
                    CatRow("Alle", categories.sumOf { counts[it.id] ?: 0 }, selectedCategory == null) { onCategory(null) }
                }
                items(categories, key = { it.id }) { cat ->
                    CatRow(cat.name, counts[cat.id] ?: 0, selectedCategory == cat.id) { onCategory(cat.id) }
                }
            }
        }

        Column(Modifier.weight(0.74f).fillMaxHeight().padding(start = 4.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(start = 8.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                if (subtitle != null) {
                    Spacer(Modifier.width(10.dp))
                    Text(subtitle, fontSize = 13.sp, color = Color.Gray)
                }
                Spacer(Modifier.weight(1f))
                SortButton(sort, onSort)
            }
            when {
                loading -> CenterLoading(loadingText)
                isEmpty -> CenterText(emptyText)
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(120.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.fillMaxSize(),
                    content = grid,
                )
            }
        }
    }
}
