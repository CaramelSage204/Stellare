package com.example.stellare.ui.reviews

import com.example.stellare.ui.RelativeTime
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.stellare.data.model.RatingModel
import com.example.stellare.ui.profile.ProfileViewModel
import com.example.stellare.ui.components.EmptyListPlaceholder
import com.example.stellare.ui.components.RatingStars
import com.example.stellare.ui.LocalClock

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyRatingsScreen(
    viewModel: ProfileViewModel,
    onBack: () -> Unit
) {
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val reviewsState = if (currentUser != null) {
        viewModel.reviewsFor(currentUser!!.id).collectAsStateWithLifecycle(emptyList())
    } else {
        remember { mutableStateOf(emptyList()) }
    }
    val reviews = reviewsState.value
    val ratingState = if (currentUser != null) {
        remember(currentUser!!.id) { viewModel.ratingFor(currentUser!!.id) }.collectAsStateWithLifecycle(RatingModel())
    } else {
        remember { mutableStateOf(RatingModel()) }
    }
    val rating = ratingState.value

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Moje Oceny i Opinie", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Powrót")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            currentUser?.let { user ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Średnia Twoich Ocen", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = myRatingAverageText(rating),
                            fontSize = 64.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        RatingStars(average = rating.average, starSize = 24.dp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(myRatingBasisText(rating), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                }

                Text("Historia Opinii", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 12.dp))

                if (reviews.isEmpty()) {
                    EmptyListPlaceholder(message = "Nie otrzymałeś jeszcze żadnych opinii.")
                } else {
                    reviews.forEach { rev ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(rev.reviewerName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Row {
                                        repeat(rev.rating) {
                                            Icon(Icons.Default.Star, "Ocena", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(12.dp))
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(rev.comment, fontSize = 13.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = RelativeTime.format(rev.timestamp, LocalClock.current()),
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
