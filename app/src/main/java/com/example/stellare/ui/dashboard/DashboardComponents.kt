package com.example.stellare.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.stellare.ui.RelativeTime
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.stellare.data.model.PostModel
import com.example.stellare.data.model.PostType
import com.example.stellare.data.model.UserModel
import com.example.stellare.ui.components.SpecializationChip
import com.example.stellare.ui.feed.FeedViewModel
import com.example.stellare.ui.constants.ALL_SPECIALIZATIONS
import com.example.stellare.ui.theme.RatingStar

private const val MAX_FILTERS_HEIGHT_FRACTION = 0.6f

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FiltersSelectionCard(
    viewModel: FeedViewModel,
    onDismiss: () -> Unit
) {
    val filters by viewModel.filters.collectAsStateWithLifecycle()
    var tempTags by remember { mutableStateOf(filters.tags) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = (LocalConfiguration.current.screenHeightDp * MAX_FILTERS_HEIGHT_FRACTION).dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text("Filtrowanie wyników", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(16.dp))

            Text("Tagi ogłoszeń", fontSize = 13.sp, fontWeight = FontWeight.Medium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            ) {
                ALL_SPECIALIZATIONS.forEach { tag ->
                    val isSelected = tempTags.contains(tag)
                    SpecializationChip(
                        label = tag,
                        isSelected = isSelected,
                        onClick = {
                            tempTags = if (isSelected) tempTags - tag else tempTags + tag
                        }
                    )
                }
            }

            Button(
                onClick = {
                    viewModel.updateFilters(filters.copy(tags = tempTags))
                    onDismiss()
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Zastosuj")
            }
        }
    }
}
