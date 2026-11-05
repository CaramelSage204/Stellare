package com.example.stellare.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.stellare.ui.theme.stellareButtonColors
import com.example.stellare.ui.theme.stellareIconButtonColors
import com.example.stellare.ui.theme.stellareOutlinedButtonColors
import com.example.stellare.ui.theme.stellareOutlinedButtonBorder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeleteAccountScreen(
    viewModel: AccountDeletionViewModel,
    onBack: () -> Unit
) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val balance by viewModel.balance.collectAsStateWithLifecycle()
    var understood by remember { mutableStateOf(false) }
    val deleting = state == AccountDeletionState.Deleting

    val leave = {
        viewModel.dismissOutcome()
        onBack()
    }
    BackHandler(enabled = deleting) {}
    BackHandler(enabled = !deleting, onBack = leave)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Usuń konto", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = leave, enabled = !deleting, colors = stellareIconButtonColors()) {
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
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(AccountDeletionDisclosure.INTRO, style = MaterialTheme.typography.bodyLarge)

            val user = profile
            if (user != null) {
                DisclosureList(
                    title = "Zniknie",
                    items = AccountDeletionDisclosure.removed(user.role, balance)
                )
                KeptList(items = AccountDeletionDisclosure.kept(user.role))
            }

            OutcomePanel(
                state = state,
                onRetry = viewModel::deleteAccount,
                onRelogin = viewModel::signOutToRelogin,
                onClose = leave,
                onStay = viewModel::dismissOutcome
            )

            val canStart = user != null && state == AccountDeletionState.Idle
            ConfirmationRow(
                checked = understood,
                enabled = canStart,
                onCheckedChange = { understood = it }
            )
            Button(
                onClick = viewModel::deleteAccount,
                enabled = canStart && understood,
                colors = stellareButtonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {
                Text("Usuń konto")
            }
            OutlinedButton(
                onClick = leave,
                enabled = !deleting,
                colors = stellareOutlinedButtonColors(),
                border = stellareOutlinedButtonBorder(enabled = !deleting),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {
                Text("Anuluj")
            }
        }
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun DisclosureList(title: String, items: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionHeading(title)
        items.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun KeptList(items: List<KeptItem>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeading("Zostanie")
        items.forEach { item ->
            Column {
                Text("• ${item.what}", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = item.why,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun ConfirmationRow(checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = onCheckedChange
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "Rozumiem, co zniknie, a co zostanie, i że nie da się tego cofnąć.",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun OutcomePanel(
    state: AccountDeletionState,
    onRetry: () -> Unit,
    onRelogin: () -> Unit,
    onClose: () -> Unit,
    onStay: () -> Unit
) {
    when (state) {
        AccountDeletionState.Idle -> Unit
        AccountDeletionState.Deleting -> Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        ) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text("Usuwanie konta. Nie zamykaj aplikacji.")
        }
        AccountDeletionState.RecentLoginRequired -> ErrorCard(
            message = AccountDeletionMessages.RECENT_LOGIN_REQUIRED
        ) {
            Button(
                onClick = onRelogin,
                colors = stellareButtonColors(),
                modifier = Modifier.heightIn(min = 48.dp)
            ) { Text("Wyloguj i zaloguj ponownie") }
            OutlinedButton(onClick = onStay, border = stellareOutlinedButtonBorder(), modifier = Modifier.heightIn(min = 48.dp)) { Text("Zostań") }
        }
        is AccountDeletionState.Failed -> ErrorCard(message = state.message) {
            Button(
                onClick = onRetry,
                colors = stellareButtonColors(),
                modifier = Modifier.heightIn(min = 48.dp)
            ) { Text("Ponów") }
            OutlinedButton(onClick = onClose, border = stellareOutlinedButtonBorder(), modifier = Modifier.heightIn(min = 48.dp)) { Text("Zamknij") }
        }
    }
}

@Composable
private fun ErrorCard(message: String, actions: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        ),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Assertive }
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(message, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { actions() }
        }
    }
}
