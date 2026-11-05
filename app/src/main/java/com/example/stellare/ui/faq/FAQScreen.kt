package com.example.stellare.ui.faq

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FAQScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Zalety i FAQ Stellare", maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
