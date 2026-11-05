package com.example.stellare.ui.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.stellare.ui.theme.coinGold
import com.example.stellare.ui.theme.stellareButtonColors

@Composable
internal fun WalletDetailsDialog(onDismiss: () -> Unit) {
    val bgCard = MaterialTheme.colorScheme.surfaceVariant
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.primary

    AlertDialog(
        onDismissRequest = { onDismiss() },
        title = { Text("O Portfelu WKT", color = textPrimary) },
        text = {
            Text(
                text = "Monety Stellare (WKT) służą do rezerwowania wizyt u psychologów. W tej wersji doładowanie jest symulowane: monety dodajemy do portfela od razu, bez pobierania płatności.",
                color = textSecondary,
                modifier = Modifier.verticalScroll(rememberScrollState())
            )
        },
        confirmButton = {
            TextButton(onClick = { onDismiss() }) {
                Text("Rozumiem", color = accent)
            }
        },
        containerColor = bgCard
    )
}

@Composable
internal fun TopUpCheckoutDialog(
    pack: CoinPack,
    topUpError: String?,
    topUpInProgress: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onDismissRequest: () -> Unit
) {
    val bgCard = MaterialTheme.colorScheme.surfaceVariant
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    val coinGold = MaterialTheme.coinGold
    val errorRed = MaterialTheme.colorScheme.error

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Doładuj Portfel Monet", color = textPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("Wybrany pakiet: ${pack.name}", color = textPrimary, fontWeight = FontWeight.Bold)
                Text("Dodaje do konta: +${pack.coins} monet WKT", color = coinGold, fontWeight = FontWeight.SemiBold)
                Text("Wartość pakietu: ${pack.price}", color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp))
                Text(TopUpNotice.NO_PAYMENT, color = textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
                topUpError?.let {
                    Text(it, color = errorRed, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !topUpInProgress,
                colors = stellareButtonColors(containerColor = accent)
            ) {
                Text(if (topUpInProgress) "Przetwarzanie..." else "Doładuj monety")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Anuluj", color = textSecondary)
            }
        },
        containerColor = bgCard
    )
}

@Composable
internal fun TopUpSuccessDialog(addedCoins: Int, onDismiss: () -> Unit) {
    val bgCard = MaterialTheme.colorScheme.surfaceVariant
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    val positiveGreen = MaterialTheme.colorScheme.tertiary

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = bgCard)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = positiveGreen,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Doładowanie udane! 🎉",
                    color = textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Portfel został zasilony kwotą +$addedCoins WKT.",
                    color = textSecondary,
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = accent),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Super!", color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CoinPacksDialog(
    packs: List<CoinPack>,
    onPackSelected: (CoinPack) -> Unit,
    onDismiss: () -> Unit
) {
    val bgMain = MaterialTheme.colorScheme.background
    val bgCard = MaterialTheme.colorScheme.surfaceVariant
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    val coinGold = MaterialTheme.coinGold

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Doładuj Portfel (Pakiety)", color = textPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(TopUpNotice.NO_PAYMENT, color = textSecondary, fontSize = 12.sp)
                packs.forEach { pack ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPackSelected(pack) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = bgMain)
                    ) {
                        FlowRow(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.weight(1f).align(Alignment.CenterVertically),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(coinGold.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Paid,
                                        contentDescription = null,
                                        tint = coinGold,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${pack.name} (+${pack.coins} WKT)",
                                        color = textPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = pack.description,
                                        color = textSecondary,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                            Column(
                                modifier = Modifier
                                    .align(Alignment.CenterVertically)
                                    .background(accent, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                    .semantics(mergeDescendants = true) {
                                        contentDescription = TopUpNotice.packValueDescription(pack)
                                    },
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = TopUpNotice.PACK_VALUE_LABEL,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontSize = 9.sp
                                )
                                Text(
                                    text = pack.price,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Zamknij", color = textSecondary)
            }
        },
        containerColor = bgCard
    )
}
