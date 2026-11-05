package com.example.stellare.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.stellare.data.settings.AppSettings
import com.example.stellare.data.settings.BubbleColorChoice
import com.example.stellare.data.settings.FontScaleChoice
import com.example.stellare.data.settings.ThemeChoice

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onOpenPhoneVerification: () -> Unit,
    onOpenFaq: () -> Unit,
    onOpenDeleteAccount: () -> Unit
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val current = settings ?: AppSettings()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ustawienia", maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            ChoiceSection(
                title = "Motyw",
                options = ThemeChoice.entries,
                selected = current.theme,
                label = ThemeChoice::label,
                onSelect = viewModel::setTheme
            )
            SectionDivider()
            ChoiceSection(
                title = "Rozmiar czcionki",
                options = FontScaleChoice.entries,
                selected = current.fontScale,
                label = FontScaleChoice::label,
                onSelect = viewModel::setFontScale
            )
            SectionDivider()
            ChoiceSection(
                title = "Kolor dymków",
                options = BubbleColorChoice.entries,
                selected = current.bubbleColor,
                label = BubbleColorChoice::label,
                onSelect = viewModel::setBubbleColor
            )
            SectionDivider()
            AccountSection(
                onOpenPhoneVerification = onOpenPhoneVerification,
                onOpenFaq = onOpenFaq,
                onOpenDeleteAccount = onOpenDeleteAccount
            )
        }
    }
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

@Composable
private fun <T> ChoiceSection(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit
) {
    Column(modifier = Modifier.selectableGroup()) {
        SectionTitle(title)
        options.forEach { option ->
            ChoiceRow(
                label = label(option),
                selected = option == selected,
                onSelect = { onSelect(option) }
            )
        }
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = label, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun AccountSection(
    onOpenPhoneVerification: () -> Unit,
    onOpenFaq: () -> Unit,
    onOpenDeleteAccount: () -> Unit
) {
    Column {
        SectionTitle("Konto i pomoc")
        NavigationRow(
            icon = Icons.Filled.Phone,
            label = "Numer telefonu",
            onClick = onOpenPhoneVerification
        )
        NavigationRow(
            icon = Icons.Filled.HelpOutline,
            label = "Zalety i FAQ Stellare",
            onClick = onOpenFaq
        )
        NavigationRow(
            icon = Icons.Filled.DeleteForever,
            label = "Usuń konto",
            onClick = onOpenDeleteAccount,
            tint = MaterialTheme.colorScheme.error,
            labelColor = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun NavigationRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.primary,
    labelColor: Color = Color.Unspecified
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint)
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = label, color = labelColor, modifier = Modifier.weight(1f))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
