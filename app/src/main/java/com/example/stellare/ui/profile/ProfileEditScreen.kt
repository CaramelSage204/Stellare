package com.example.stellare.ui.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.stellare.ui.psychologistdetail.ReviewsLoad
import kotlinx.coroutines.flow.emptyFlow
import com.example.stellare.ui.theme.stellareButtonColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditScreen(
    viewModel: ProfileViewModel,
    onBack: () -> Unit
) {
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val privateProfile by viewModel.privateProfile.collectAsStateWithLifecycle()
    val saveState by viewModel.saveState.collectAsStateWithLifecycle()
    val saving = saveState is ProfileSaveState.InProgress
    val specialist = isSpecialist(currentUser?.role)
    val userId = currentUser?.id
    var pricePerSessionString by rememberSaveable { mutableStateOf("") }
    val priceMessage = priceError(specialist, pricePerSessionString)

    val reviewsLoad by remember(userId, specialist) {
        if (specialist && !userId.isNullOrEmpty()) viewModel.reviewsLoadFor(userId) else emptyFlow()
    }.collectAsStateWithLifecycle<ReviewsLoad?>(null)
    val reviewSummary = profileReviewSummary(specialist, reviewsLoad)

    var hydratedUserId by rememberSaveable { mutableStateOf<String?>(null) }
    var profileImageUriString by rememberSaveable { mutableStateOf<String?>(null) }
    var bio by rememberSaveable { mutableStateOf("") }
    var specializations by rememberSaveable { mutableStateOf("") }

    var showAddPriceDialog by remember { mutableStateOf(false) }
    var selectedServiceType by remember { mutableStateOf(DefaultServiceType) }
    var newPriceValueString by remember { mutableStateOf("") }
    var localPricesList by rememberSaveable { mutableStateOf(listOf<Pair<String, Int>>()) }
    var unreadablePrices by rememberSaveable { mutableStateOf(listOf<String>()) }

    var showSpecSelectionDialog by remember { mutableStateOf(false) }
    var selectedSpecsSet by rememberSaveable { mutableStateOf(setOf<String>()) }
    var tempSelectedSpecs by remember { mutableStateOf(setOf<String>()) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { profileImageUriString = it.toString() }
    }

    LaunchedEffect(saveState) {
        if (saveState is ProfileSaveState.Success) {
            viewModel.resetSaveState()
            onBack()
        }
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.resetSaveState() }
    }

    LaunchedEffect(currentUser) {
        currentUser?.let {
            if (!shouldHydrateProfileForm(hydratedUserId, it.id)) return@let
            hydratedUserId = it.id
            profileImageUriString = it.profileImageUri
            bio = it.bio
            specializations = it.specializations
            selectedSpecsSet = specializationSet(it.specializations)
            pricePerSessionString = sessionPriceText(it.pricePerSession)

            val parsedPrices = CustomPrices.parse(it.customPrices)
            localPricesList = parsedPrices.entries
            unreadablePrices = parsedPrices.unreadable
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edycja Profilu", maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
            ProfileEditHeader(
                user = currentUser,
                email = privateProfile?.email.orEmpty(),
                imageUri = profileImageUriString,
                reviewSummary = reviewSummary,
                onPickImage = { imagePickerLauncher.launch("image/*") }
            )

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = bio,
                onValueChange = { bio = it },
                label = { Text("O mnie / Opis zgłoszenia") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp).padding(bottom = 16.dp)
            )

            if (specialist) {
                SpecializationsCard(
                    specializations = selectedSpecsSet,
                    onClick = {
                        tempSelectedSpecs = selectedSpecsSet
                        showSpecSelectionDialog = true
                    },
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                OutlinedTextField(
                    value = pricePerSessionString,
                    onValueChange = { pricePerSessionString = it },
                    label = { Text("Cena domyślna sesji (WKT)") },
                    isError = priceMessage != null,
                    supportingText = { priceMessage?.let { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                )

                CustomPricesSection(
                    prices = localPricesList,
                    onAdd = {
                        selectedServiceType = DefaultServiceType
                        newPriceValueString = ""
                        showAddPriceDialog = true
                    },
                    onRemove = { index -> localPricesList = localPricesList.withoutIndex(index) },
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                enabled = canSave(currentUser != null, priceMessage) && !saving,
                onClick = {
                    val user = currentUser ?: return@Button
                    val price = priceToSave(specialist, pricePerSessionString, user.pricePerSession)
                        ?: return@Button
                    viewModel.updateProfile(
                        firstName = user.firstName,
                        lastName = user.lastName,
                        age = user.age,
                        gender = user.gender,
                        bio = bio,
                        specializations = specializations,
                        pricePerSession = price,
                        customPrices = CustomPrices(localPricesList, unreadablePrices).format(),
                        profileImageUri = profileImageUriString
                    )
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                colors = stellareButtonColors()
            ) {
                Text(if (saving) "Zapisywanie..." else "Zapisz Zmiany", fontWeight = FontWeight.Bold)
            }

            saveOutcomeMessage(saveState)?.let { (message, color) ->
                Text(
                    text = message,
                    color = color,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
        }
    }

    if (showAddPriceDialog) {
        AddPriceDialog(
            selectedType = selectedServiceType,
            priceText = newPriceValueString,
            priceError = newServiceError(newPriceValueString),
            onTypeChange = { selectedServiceType = it },
            onPriceChange = { newPriceValueString = it },
            onConfirm = {
                newServicePrice(newPriceValueString)?.let { price ->
                    localPricesList = localPricesList + (selectedServiceType to price)
                    showAddPriceDialog = false
                }
            },
            onDismiss = { showAddPriceDialog = false }
        )
    }

    if (showSpecSelectionDialog) {
        SpecializationDialog(
            selected = tempSelectedSpecs,
            onToggle = { tempSelectedSpecs = tempSelectedSpecs.toggled(it) },
            onConfirm = {
                selectedSpecsSet = tempSelectedSpecs
                specializations = specializationsText(tempSelectedSpecs)
                showSpecSelectionDialog = false
            },
            onDismiss = { showSpecSelectionDialog = false }
        )
    }
}

@Composable
private fun saveOutcomeMessage(state: ProfileSaveState): Pair<String, Color>? = when (state) {
    is ProfileSaveState.Error -> state.message to MaterialTheme.colorScheme.error
    is ProfileSaveState.Uncertain -> state.message to MaterialTheme.colorScheme.onSurfaceVariant
    else -> null
}
