package com.example.stellare.ui.posts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.stellare.data.model.UserRole
import com.example.stellare.ui.constants.ALL_SPECIALIZATIONS
import com.example.stellare.ui.dashboard.SpecializationChip
import com.example.stellare.ui.profile.ProfileViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PostComposerScreen(
    viewModel: PostViewModel,
    profileViewModel: ProfileViewModel,
    onBack: () -> Unit
) {
    val currentUser by profileViewModel.currentUser.collectAsStateWithLifecycle()
    val publishError by viewModel.publishError.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()

    var title by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    var selectedTags by rememberSaveable { mutableStateOf(setOf<String>()) }

    val isPsychologist = currentUser?.role == UserRole.PSYCHOLOGIST ||
        currentUser?.role == UserRole.PSYCHOLOGY_STUDENT

    DisposableEffect(Unit) {
        onDispose { viewModel.clearPublishError() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Napisz ogłoszenie") },
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
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Tytuł") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text("Treść ogłoszenia") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                shape = RoundedCornerShape(12.dp)
            )

            Text(
                text = "Tagi",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.primary
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                ALL_SPECIALIZATIONS.forEach { specialization ->
                    val isSelected = selectedTags.contains(specialization)
                    SpecializationChip(
                        text = specialization,
                        isSelected = isSelected,
                        onClick = {
                            selectedTags = if (isSelected) {
                                selectedTags - specialization
                            } else {
                                selectedTags + specialization
                            }
                        }
                    )
                }
            }

            if (isPsychologist) {
                OutlinedTextField(
                    value = "${currentUser?.pricePerSession?.toInt() ?: 0} WKT",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Cena za sesję (z Twojego profilu)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            if (publishError != null) {
                Text(
                    text = publishError ?: "",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 14.sp
                )
            }

            Button(
                onClick = {
                    coroutineScope.launch {
                        if (viewModel.publish(title, body, selectedTags.toList())) {
                            onBack()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Opublikuj", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}
