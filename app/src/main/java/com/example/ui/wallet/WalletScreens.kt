package com.example.ui.wallet

import android.text.format.DateUtils
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.WektorViewModel

@Composable
fun WalletScreen(
    viewModel: WektorViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val transactions by viewModel.walletTransactions.collectAsStateWithLifecycle()
    
    // UI states
    var showPacksDialog by remember { mutableStateOf(false) }
    
    // Dialog / Overlay controls
    var showCheckoutDialog by remember { mutableStateOf<CoinPack?>(null) }
    var selectedPaymentMethod by remember { mutableStateOf("BLIK") }
    var checkoutCode by remember { mutableStateOf("") }
    var showSuccessOverlay by remember { mutableStateOf(false) }
    var lastAddedCoins by remember { mutableStateOf(0) }
    
    var showTransferDialog by remember { mutableStateOf(false) }
    var transferRecipient by remember { mutableStateOf("") }
    var transferAmount by remember { mutableStateOf("") }
    var showTransferSuccess by remember { mutableStateOf(false) }
    
    var showWithdrawDialog by remember { mutableStateOf(false) }
    var withdrawAddress by remember { mutableStateOf("") }
    var withdrawAmount by remember { mutableStateOf("") }
    var showWithdrawSuccess by remember { mutableStateOf(false) }
    
    var showDetailsMenu by remember { mutableStateOf(false) }

    val coinPacks = listOf(
        CoinPack("Pakiet Mały", 50, "50,00 PLN", "Idealny na start i przetestowanie wizyty"),
        CoinPack("Pakiet Standardowy", 100, "100,00 PLN", "Najbardziej popularny pakiet"),
        CoinPack("Pakiet Profesjonalny", 250, "250,00 PLN", "Najlepsza wartość i wygoda")
    )

    // Dark-themed Telegram colors
    val bgMain = Color(0xFF0F0F10)
    val bgCard = Color(0xFF1C1D22)
    val textPrimary = Color(0xFFFFFFFF)
    val textSecondary = Color(0xFF8E8E93)
    val telegramBlue = Color(0xFF24A1DE)
    val coinGold = Color(0xFFFFD700)
    val positiveGreen = Color(0xFF2E7D32)
    val errorRed = Color(0xFFE53935)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = bgMain
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Premium Telegram App Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(
                    onClick = onBack,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        text = "Close",
                        color = textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Text(
                    text = "Portfel",
                    color = textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )

                IconButton(
                    onClick = { showDetailsMenu = !showDetailsMenu }
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreHoriz,
                        contentDescription = "Więcej",
                        tint = textPrimary
                    )
                }
            }

            // Profile row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left avatar circle
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF3B3C42)),
                    contentAlignment = Alignment.Center
                ) {
                    val initial = currentUser?.firstName?.take(1) ?: "U"
                    Text(
                        text = initial,
                        color = textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }

                // Right Scanner/QR Icon
                IconButton(
                    onClick = {
                        android.widget.Toast.makeText(context, "Skaner kodów QR zostanie uruchomiony wkrótce!", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .size(38.dp)
                        .background(bgCard, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = "Skanuj QR",
                        tint = textPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Scrollable Content
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {

                // Main Balance Display
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${currentUser?.coinsBalance ?: 0}",
                            fontSize = 52.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "WKT",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            color = coinGold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    // 1:1 estimate (1 WKT = 1 PLN)
                    val plnValue = (currentUser?.coinsBalance ?: 0).toDouble()
                    Text(
                        text = "~ ${String.format("%.2f", plnValue)} PLN",
                        fontSize = 15.sp,
                        color = textSecondary,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Quick Action Buttons Underneath Balance
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Deposit
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { showPacksDialog = true }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .background(bgCard, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Doładuj",
                                tint = textPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Deposit", color = textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }

                    // Withdraw
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { showWithdrawDialog = true }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .background(bgCard, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowUpward,
                                contentDescription = "Wypłać",
                                tint = textPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Withdraw", color = textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Ledger history
                Text(
                    text = "Historia portfela",
                    color = textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                if (transactions.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = bgCard)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = textSecondary.copy(alpha = 0.5f),
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Brak transakcji w historii",
                                color = textPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        }
                    }
                } else {
                    transactions.forEach { tx ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            colors = CardDefaults.cardColors(containerColor = bgCard)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (tx.amount > 0) positiveGreen.copy(alpha = 0.15f)
                                            else errorRed.copy(alpha = 0.15f)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (tx.amount > 0) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                                        contentDescription = null,
                                        tint = if (tx.amount > 0) positiveGreen else errorRed,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = tx.title,
                                        color = textPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                    val dateStr = DateUtils.getRelativeTimeSpanString(
                                        tx.timestamp,
                                        System.currentTimeMillis(),
                                        DateUtils.MINUTE_IN_MILLIS
                                    ).toString()
                                    Text(
                                        text = dateStr,
                                        color = textSecondary,
                                        fontSize = 10.sp
                                    )
                                }
                                Text(
                                    text = if (tx.amount > 0) "+${tx.amount} WKT" else "${tx.amount} WKT",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (tx.amount > 0) positiveGreen else errorRed
                                )
                            }
                        }
                    }
                }
            }
        }

        // Action pop-ups & Dialogs
        
        // 1. Dropdown Info Details
        if (showDetailsMenu) {
            AlertDialog(
                onDismissRequest = { showDetailsMenu = false },
                title = { Text("O Portfelu WKT", color = textPrimary) },
                text = {
                    Text(
                        text = "System portfela monet pozwala Ci bezpiecznie kupować i przesyłać monety Wektor (WKT). Dzięki monetom rezerwujesz wizyty u psychologów bez podawania danych bankowych przy każdej transakcji.",
                        color = textSecondary
                    )
                },
                confirmButton = {
                    TextButton(onClick = { showDetailsMenu = false }) {
                        Text("Rozumiem", color = telegramBlue)
                    }
                },
                containerColor = bgCard
            )
        }

        // 2. Buy Coins (Deposit)
        if (showCheckoutDialog != null) {
            val pack = showCheckoutDialog!!
            AlertDialog(
                onDismissRequest = { showCheckoutDialog = null },
                title = { Text("Doładuj Portfel Monet", color = textPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text("Wybrany pakiet: ${pack.name}", color = textPrimary, fontWeight = FontWeight.Bold)
                        Text("Dodaje do konta: +${pack.coins} monet WKT", color = coinGold, fontWeight = FontWeight.SemiBold)
                        Text("Cena: ${pack.price}", color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp))
                        
                        Spacer(modifier = Modifier.height(14.dp))
                        Text("Metoda płatności:", color = textSecondary, fontSize = 12.sp)
                        
                        listOf("BLIK", "Karta płatnicza", "Przelew bankowy").forEach { method ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .background(
                                        if (selectedPaymentMethod == method) telegramBlue.copy(alpha = 0.1f) else Color.Transparent,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (selectedPaymentMethod == method) telegramBlue else textSecondary.copy(alpha = 0.2f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { selectedPaymentMethod = method }
                                    .padding(10.dp)
                            ) {
                                RadioButton(
                                    selected = selectedPaymentMethod == method,
                                    onClick = { selectedPaymentMethod = method },
                                    colors = RadioButtonDefaults.colors(selectedColor = telegramBlue, unselectedColor = textSecondary)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(method, color = textPrimary, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (selectedPaymentMethod == "BLIK") {
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(
                                value = checkoutCode,
                                onValueChange = { checkoutCode = it },
                                label = { Text("Kod BLIK (6 cyfr)", color = textSecondary) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = telegramBlue,
                                    unfocusedBorderColor = textSecondary.copy(alpha = 0.3f),
                                    focusedTextColor = textPrimary,
                                    unfocusedTextColor = textPrimary
                                )
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.topUpWallet(pack.coins)
                            lastAddedCoins = pack.coins
                            showCheckoutDialog = null
                            checkoutCode = ""
                            showSuccessOverlay = true
                        },
                        enabled = selectedPaymentMethod != "BLIK" || checkoutCode.length == 6,
                        colors = ButtonDefaults.buttonColors(containerColor = telegramBlue)
                    ) {
                        Text("Zapłać i doładuj", color = textPrimary)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCheckoutDialog = null }) {
                        Text("Anuluj", color = textSecondary)
                    }
                },
                containerColor = bgCard
            )
        }

        // Success Buy Overlay
        if (showSuccessOverlay) {
            Dialog(onDismissRequest = { showSuccessOverlay = false }) {
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
                            "Portfel został zasilony kwotą +$lastAddedCoins WKT.",
                            color = textSecondary,
                            textAlign = TextAlign.Center,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { showSuccessOverlay = false },
                            colors = ButtonDefaults.buttonColors(containerColor = telegramBlue),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Super!", color = textPrimary)
                        }
                    }
                }
            }
        }



        // 4. Transfer Coins Dialog
        if (showTransferDialog) {
            AlertDialog(
                onDismissRequest = { showTransferDialog = false },
                title = { Text("Przelej monety WKT", color = textPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text("Prześlij monety natychmiastowo do innego użytkownika za pomocą jego adresu lub pseudonimu.", color = textSecondary, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        OutlinedTextField(
                            value = transferRecipient,
                            onValueChange = { transferRecipient = it },
                            label = { Text("Adres odbiorcy (np. UQDy...)", color = textSecondary) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = telegramBlue,
                                unfocusedBorderColor = textSecondary.copy(alpha = 0.3f),
                                focusedTextColor = textPrimary,
                                unfocusedTextColor = textPrimary
                            )
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = transferAmount,
                            onValueChange = { transferAmount = it },
                            label = { Text("Ilość monet WKT", color = textSecondary) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = telegramBlue,
                                unfocusedBorderColor = textSecondary.copy(alpha = 0.3f),
                                focusedTextColor = textPrimary,
                                unfocusedTextColor = textPrimary
                            )
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val amount = transferAmount.toIntOrNull() ?: 0
                            if (amount <= 0) {
                                android.widget.Toast.makeText(context, "Wpisz poprawną kwotę!", android.widget.Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (transferRecipient.isBlank()) {
                                android.widget.Toast.makeText(context, "Wpisz adres odbiorcy!", android.widget.Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            val success = viewModel.spendCoins(amount, "Przelew do: $transferRecipient")
                            if (success) {
                                showTransferDialog = false
                                showTransferSuccess = true
                                transferAmount = ""
                                transferRecipient = ""
                            } else {
                                android.widget.Toast.makeText(context, "Brak wystarczających środków!", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = telegramBlue)
                    ) {
                        Text("Wyślij przelew", color = textPrimary)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showTransferDialog = false }) {
                        Text("Anuluj", color = textSecondary)
                    }
                },
                containerColor = bgCard
            )
        }

        // Transfer Success Overlay
        if (showTransferSuccess) {
            Dialog(onDismissRequest = { showTransferSuccess = false }) {
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
                            "Przelew wysłany! 🚀",
                            color = textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Monety zostały natychmiastowo przelane na adres odbiorcy.",
                            color = textSecondary,
                            textAlign = TextAlign.Center,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { showTransferSuccess = false },
                            colors = ButtonDefaults.buttonColors(containerColor = telegramBlue),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Świetnie!", color = textPrimary)
                        }
                    }
                }
            }
        }

        // 5. Withdraw Coins Dialog
        if (showWithdrawDialog) {
            AlertDialog(
                onDismissRequest = { showWithdrawDialog = false },
                title = { Text("Wypłać monety WKT", color = textPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text("Przelej monety na konto bankowe lub tradycyjną kartę płatniczą. Minimalna kwota wypłaty: 75 monet (75 PLN). Przelicznik: 1 WKT = 1.00 PLN.", color = textSecondary, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        OutlinedTextField(
                            value = withdrawAddress,
                            onValueChange = { withdrawAddress = it },
                            label = { Text("Numer konta (IBAN) lub karty", color = textSecondary) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = telegramBlue,
                                unfocusedBorderColor = textSecondary.copy(alpha = 0.3f),
                                focusedTextColor = textPrimary,
                                unfocusedTextColor = textPrimary
                            )
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = withdrawAmount,
                            onValueChange = { withdrawAmount = it },
                            label = { Text("Ilość monet WKT do wypłaty (min. 75)", color = textSecondary) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = telegramBlue,
                                unfocusedBorderColor = textSecondary.copy(alpha = 0.3f),
                                focusedTextColor = textPrimary,
                                unfocusedTextColor = textPrimary
                            )
                        )

                        val amountToWithdraw = withdrawAmount.toIntOrNull() ?: 0
                        if (amountToWithdraw > 0) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Otrzymasz przelew na kwotę: ${String.format("%.2f", amountToWithdraw.toDouble())} PLN",
                                color = coinGold,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val amount = withdrawAmount.toIntOrNull() ?: 0
                            if (amount < 75) {
                                android.widget.Toast.makeText(context, "Minimalna kwota wypłaty to 75 WKT (75 PLN)!", android.widget.Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (withdrawAddress.isBlank()) {
                                android.widget.Toast.makeText(context, "Wpisz numer konta/karty!", android.widget.Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            val success = viewModel.spendCoins(amount, "Wypłata środków (WKT -> PLN)")
                            if (success) {
                                showWithdrawDialog = false
                                showWithdrawSuccess = true
                                withdrawAmount = ""
                                withdrawAddress = ""
                            } else {
                                android.widget.Toast.makeText(context, "Brak wystarczających środków!", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = telegramBlue)
                    ) {
                        Text("Zleć wypłatę", color = textPrimary)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showWithdrawDialog = false }) {
                        Text("Anuluj", color = textSecondary)
                    }
                },
                containerColor = bgCard
            )
        }

        // Withdraw Success Overlay
        if (showWithdrawSuccess) {
            Dialog(onDismissRequest = { showWithdrawSuccess = false }) {
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
                            "Zlecenie wypłaty przyjęte! 💳",
                            color = textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Środki zostaną zaksięgowane na Twoim koncie w ciągu 1-2 dni roboczych.",
                            color = textSecondary,
                            textAlign = TextAlign.Center,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { showWithdrawSuccess = false },
                            colors = ButtonDefaults.buttonColors(containerColor = telegramBlue),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Rozumiem", color = textPrimary)
                        }
                    }
                }
            }
        }

        // Packs Selection Dialog (Deposit)
        if (showPacksDialog) {
            AlertDialog(
                onDismissRequest = { showPacksDialog = false },
                title = { Text("Doładuj Portfel (Pakiety)", color = textPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        coinPacks.forEach { pack ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showCheckoutDialog = pack
                                        showPacksDialog = false
                                    },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = bgMain)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
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
                                    Box(
                                        modifier = Modifier
                                            .background(telegramBlue, RoundedCornerShape(6.dp))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = pack.price,
                                            color = textPrimary,
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
                    TextButton(onClick = { showPacksDialog = false }) {
                        Text("Zamknij", color = textSecondary)
                    }
                },
                containerColor = bgCard
            )
        }
    }
}

data class CoinPack(
    val name: String,
    val coins: Int,
    val price: String,
    val description: String
)