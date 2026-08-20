package com.example.stellare.ui.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.stellare.data.model.UserRole
import com.example.stellare.ui.StellareViewModel
import com.example.stellare.ui.dashboard.SpecializationChip

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProfileEditScreen(
    viewModel: StellareViewModel,
    onBack: () -> Unit
) {
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
 
    var profileImageUriString by remember { mutableStateOf<String?>(null) }
    var bio by remember { mutableStateOf("") }
    var specializations by remember { mutableStateOf("") }
    var pricePerSessionString by remember { mutableStateOf("") }
 
    var showAddPriceDialog by remember { mutableStateOf(false) }
    var selectedServiceType by remember { mutableStateOf("Sesja audio") }
    var newPriceValueString by remember { mutableStateOf("") }
    var localPricesList by remember { mutableStateOf(listOf<Pair<String, Int>>()) }
 
    var showSpecSelectionDialog by remember { mutableStateOf(false) }
    var selectedSpecsSet by remember { mutableStateOf(setOf<String>()) }
    var tempSelectedSpecs by remember { mutableStateOf(setOf<String>()) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            profileImageUriString = it.toString()
        }
    }
 
    LaunchedEffect(currentUser) {
        currentUser?.let {
            profileImageUriString = it.profileImageUri
            bio = it.bio
            specializations = it.specializations
            selectedSpecsSet = it.specializations.split(",").map { s -> s.trim() }.filter { s -> s.isNotEmpty() }.toSet()
            pricePerSessionString = it.pricePerSession.toInt().toString()

            val rawPrices = it.customPrices ?: ""
            localPricesList = if (rawPrices.isBlank()) {
                emptyList()
            } else {
                rawPrices.split(";").mapNotNull { part ->
                    val pieces = part.split("-")
                    if (pieces.size == 2) {
                        val service = pieces[0].trim()
                        val price = pieces[1].replace("PLN", "").replace("WKT", "").trim().toIntOrNull() ?: 0
                        service to price
                    } else null
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edycja Profilu") },
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
            // Big avatar circle with pencil icon on bottom-right
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .align(Alignment.CenterHorizontally),
                contentAlignment = Alignment.BottomEnd
            ) {
                if (!profileImageUriString.isNullOrEmpty()) {
                    AsyncImage(
                        model = profileImageUriString,
                        contentDescription = "Zdjęcie profilowe",
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                            .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Avatar",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(72.dp)
                        )
                    }
                }

                // Edit pencil button
                IconButton(
                    onClick = { imagePickerLauncher.launch("image/*") },
                    modifier = Modifier
                        .size(36.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Zmień zdjęcie",
                        tint = Color.Black,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            
            // Your info text section
            Text(
                text = currentUser?.let { "${it.firstName} ${it.lastName}" } ?: "",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            
            Text(
                text = when (currentUser?.role) {
                    UserRole.PSYCHOLOGIST -> "Psycholog"
                    UserRole.PSYCHOLOGY_STUDENT -> "Student Psychologii"
                    else -> "Pacjent"
                } + " • ${currentUser?.age ?: 30} lat",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp)
            )
            
            Text(
                text = currentUser?.email ?: "",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 2.dp, bottom = 24.dp)
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), modifier = Modifier.padding(bottom = 24.dp))

            OutlinedTextField(
                value = bio,
                onValueChange = { bio = it },
                label = { Text("O mnie / Opis zgłoszenia") },
                modifier = Modifier.fillMaxWidth().height(120.dp).padding(bottom = 16.dp)
            )

            if (currentUser?.role == UserRole.PSYCHOLOGIST || currentUser?.role == UserRole.PSYCHOLOGY_STUDENT) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .clickable {
                            tempSelectedSpecs = selectedSpecsSet
                            showSpecSelectionDialog = true
                        },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Moje Specjalizacje",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edytuj",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        if (selectedSpecsSet.isEmpty()) {
                            Text(
                                text = "Brak wybranych specjalizacji. Kliknij aby wybrać.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        } else {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                selectedSpecsSet.forEach { spec ->
                                    SpecializationChip(
                                        text = spec,
                                        isSelected = true
                                    )
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = pricePerSessionString,
                    onValueChange = { pricePerSessionString = it },
                    label = { Text("Cena domyślna sesji (WKT/h)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                )

                // "Moje Ceny" Crud section
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Moje Ceny i Rodzaje Zajęć",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(
                        onClick = {
                            selectedServiceType = "Sesja audio"
                            newPriceValueString = ""
                            showAddPriceDialog = true
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Dodaj cenę", tint = MaterialTheme.colorScheme.primary)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (localPricesList.isEmpty()) {
                    Text(
                        text = "Brak skonfigurowanych stawek niestandardowych. Kliknij ikonę plus, aby dodać stawkę za sesję audio/wideo lub powiadomienia.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        localPricesList.forEachIndexed { index, item ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = when {
                                                item.first.contains("wideo", ignoreCase = true) -> Icons.Default.Videocam
                                                item.first.contains("audio", ignoreCase = true) -> Icons.Default.Call
                                                else -> Icons.Default.Notifications
                                            },
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(item.first, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                            Text("${item.second} WKT / godzina", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                        }
                                    }
                                    IconButton(
                                        onClick = {
                                            localPricesList = localPricesList.filterIndexed { idx, _ -> idx != index }
                                        }
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Usuń", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Pop-up dialog for adding a new service price
            if (showAddPriceDialog) {
                AlertDialog(
                    onDismissRequest = { showAddPriceDialog = false },
                    title = { Text("Dodaj Rodzaj Zajęć i Cenę", fontWeight = FontWeight.Bold) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("1. Wybierz rodzaj zajęć:", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            
                            listOf("Sesja audio", "Sesja wideo", "Powiadomienia terapeutyczne").forEach { type ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedServiceType = type }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = selectedServiceType == type,
                                        onClick = { selectedServiceType = type }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(type)
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text("2. Wpisz stawkę w WKT (za godzinę):", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            OutlinedTextField(
                                value = newPriceValueString,
                                onValueChange = { newPriceValueString = it },
                                label = { Text("Cena w WKT") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val pr = newPriceValueString.toIntOrNull() ?: 100
                                localPricesList = localPricesList + (selectedServiceType to pr)
                                showAddPriceDialog = false
                            }
                        ) {
                            Text("Zatwierdź", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        OutlinedButton(onClick = { showAddPriceDialog = false }) {
                            Text("Anuluj")
                        }
                    }
                )
            }

            // Pop-up dialog for choosing specializations
            if (showSpecSelectionDialog) {
                AlertDialog(
                    onDismissRequest = { showSpecSelectionDialog = false },
                    title = { 
                        Text(
                            "Wybierz Specjalizacje", 
                            fontWeight = FontWeight.Bold, 
                            color = MaterialTheme.colorScheme.onSurface
                        ) 
                    },
                    text = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                "Zaznacz specjalizacje, które posiadasz. Kliknij na odpowiednie kółka, aby się podświetliły.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            val allSpecs = listOf(
                                "Psycholog kliniczny",
                                "Psychoterapeuta",
                                "Seksuolog",
                                "Psycholog par i małżeński",
                                "Psycholog rodzinny",
                                "Psycholog dziecięcy i młodzieżowy",
                                "Psycholog szkolny",
                                "Psycholog pracy i organizacji (biznesu)",
                                "Psycholog sportu",
                                "Psycholog transportu",
                                "Interwent kryzysowy",
                                "Psycholog sądowy"
                            )
                            
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                allSpecs.forEach { specName ->
                                    val isSelected = tempSelectedSpecs.contains(specName)
                                    SpecializationChip(
                                        text = specName,
                                        isSelected = isSelected,
                                        onClick = {
                                            tempSelectedSpecs = if (isSelected) {
                                                tempSelectedSpecs - specName
                                            } else {
                                                tempSelectedSpecs + specName
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                selectedSpecsSet = tempSelectedSpecs
                                specializations = tempSelectedSpecs.joinToString(", ")
                                showSpecSelectionDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Zatwierdź", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showSpecSelectionDialog = false }) {
                            Text("Anuluj", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(16.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    val formattedPricesString = localPricesList.joinToString(";") { "${it.first} - ${it.second} WKT" }
                    viewModel.updateProfile(
                        firstName = currentUser?.firstName ?: "",
                        lastName = currentUser?.lastName ?: "",
                        age = currentUser?.age ?: 30,
                        gender = currentUser?.gender ?: "Kobieta",
                        bio = bio,
                        specializations = specializations,
                        pricePerSession = pricePerSessionString.toDoubleOrNull() ?: 0.0,
                        customPrices = formattedPricesString,
                        profileImageUri = profileImageUriString
                    )
                },
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("Zapisz Zmiany", fontWeight = FontWeight.Bold, color = Color.Black)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FAQScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("FAQ i Zalety Stellarea") },
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
            FAQItem(
                title = "Stellare: Zalety dla pacjenta",
                content = "- Introwertycy i osoby lękowe mogą łatwiej opowiedzieć o swoich problemach online niż sam na sam na żywo.\n- Szybkie i lekkie znajdowanie psychologa niezależnie od miejsca pobytu pacjenta.\n- Dobór psychologa idealnie pod swoje kryteria (wiek, płeć, specjalizacje, cena, opinie).\n- Niższe wydatki na psychoterapię dzięki modelowi łączącemu również studentów i doktorantów."
            )

            FAQItem(
                title = "Stellare: Zalety dla psychologów i studentów",
                content = "- Łatwość w odnajdywaniu nowych klientów w całej Polsce i za granicą.\n- Możliwość elastycznego dopasowania godzin pracy całkowicie pod siebie.\n- Praca całkowicie zdalna, bez konieczności tracenia czasu na dojazdy.\n- Pomoc studentom psychologii w poznaniu realnych sytuacji z klientami, nabieranie niezbędnego doświadczenia pod okiem uczelni oraz wykorzystanie teorii w bezpiecznej praktyce."
            )

            FAQItem(
                title = "Stellare: Współpraca z Uniwersytetami",
                content = "- Ułatwiamy uniwersytetom organizowanie obowiązkowych praktyk zawodowych dla studentów wyższych roczników psychologii w bezpiecznym środowisku cyfrowym."
            )

            FAQItem(
                title = "Jak przebiega weryfikacja dokumentów?",
                content = "Zgodnie z planem aplikacji, każdy psycholog chcący uzyskać status zweryfikowany musi zeskanować za pomocą naszego wbudowanego skanera dowód tożsamości, a następnie swój dyplom wyższej uczelni lub posiadane certyfikaty. Po automatycznej weryfikacji przy nazwisku pojawia się zielona plakietka zaufania."
            )
        }
    }
}

@Composable
fun FAQItem(title: String, content: String) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp).clickable { expanded = !expanded }) {
            Text(text = title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = content, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}
