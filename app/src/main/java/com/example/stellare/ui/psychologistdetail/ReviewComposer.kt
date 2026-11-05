package com.example.stellare.ui.psychologistdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.stellare.ui.profile.ReviewSubmitState
import com.example.stellare.ui.theme.stellareButtonColors
import com.example.stellare.ui.theme.stellareIconButtonColors

@Composable
fun ReviewComposer(
    state: ReviewSubmitState,
    onSubmit: (rating: Int, comment: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var rating by rememberSaveable { mutableIntStateOf(0) }
    var comment by rememberSaveable { mutableStateOf("") }
    val inProgress = state is ReviewSubmitState.InProgress

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Oceń odbytą sesję", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Row {
                (1..5).forEach { value ->
                    IconButton(
                        onClick = { rating = value },
                        enabled = !inProgress,
                        colors = stellareIconButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.testTag("review_star_$value")
                    ) {
                        Icon(
                            imageVector = if (value <= rating) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "$value z 5",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
            OutlinedTextField(
                value = comment,
                onValueChange = { comment = it },
                label = { Text("Komentarz (opcjonalnie)") },
                enabled = !inProgress,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth().testTag("review_comment")
            )
            (state as? ReviewSubmitState.Error)?.let {
                Text(
                    text = it.message,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { contentDescription = it.message }
                )
            }
            Button(
                onClick = { onSubmit(rating, comment.trim()) },
                enabled = rating in 1..5 && !inProgress,
                modifier = Modifier.fillMaxWidth().testTag("review_submit"),
                colors = stellareButtonColors()
            ) {
                Text("Wystaw opinię")
            }
        }
    }
}
