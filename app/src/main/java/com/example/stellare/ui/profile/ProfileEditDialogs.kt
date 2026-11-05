package com.example.stellare.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.stellare.ui.components.dialogTextScroll
import com.example.stellare.ui.components.SpecializationChip
import com.example.stellare.ui.theme.stellareButtonColors
import com.example.stellare.ui.theme.stellareOutlinedButtonBorder

const val DefaultServiceType = "Sesja audio"

private val ServiceTypes = listOf(DefaultServiceType, "Sesja wideo")

private val AllSpecializations = listOf(
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

@Composable
fun AddPriceDialog(
    selectedType: String,
    priceText: String,
    priceError: String?,
    onTypeChange: (String) -> Unit,
    onPriceChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Dodaj Rodzaj Zajęć i Cenę", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.dialogTextScroll(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("1. Wybierz rodzaj zajęć:", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                ServiceTypes.forEach { type ->
                    ServiceTypeRow(type = type, selected = selectedType == type, onSelect = { onTypeChange(type) })
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("2. Wpisz stawkę w WKT (za sesję):", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                OutlinedTextField(
                    value = priceText,
                    onValueChange = onPriceChange,
                    label = { Text("Cena w WKT") },
                    isError = priceError != null,
                    supportingText = { priceError?.let { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = priceError == null, colors = stellareButtonColors()) {
                Text("Zatwierdź", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, border = stellareOutlinedButtonBorder()) { Text("Anuluj") }
        }
    )
}

@Composable
private fun ServiceTypeRow(type: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Spacer(modifier = Modifier.width(8.dp))
        Text(type)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpecializationDialog(
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Wybierz Specjalizacje",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Zaznacz specjalizacje, które posiadasz. Kliknij na odpowiednie kółka, aby się podświetliły.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AllSpecializations.forEach { name ->
                        SpecializationChip(
                            label = name,
                            isSelected = selected.contains(name),
                            onClick = { onToggle(name) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) { Text("Zatwierdź", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Anuluj", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp)
    )
}
