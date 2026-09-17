package com.example.stellare.ui.shell

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.stellare.ui.navigation.Routes

private data class PozycjaPaska(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val POZYCJE = listOf(
    PozycjaPaska(Routes.FEED, "Ogłoszenia", Icons.Filled.Home),
    PozycjaPaska(Routes.CALENDAR, "Kalendarz", Icons.Filled.CalendarMonth),
    PozycjaPaska(Routes.CHATS, "Czaty", Icons.Filled.Chat),
    PozycjaPaska(Routes.WALLET, "Portfel", Icons.Filled.AccountBalanceWallet)
)

@Composable
fun BottomBar(currentRoute: String?, onNavigate: (String) -> Unit) {
    NavigationBar {
        POZYCJE.forEach { pozycja ->
            NavigationBarItem(
                selected = currentRoute == pozycja.route,
                onClick = { onNavigate(pozycja.route) },
                icon = { Icon(pozycja.icon, contentDescription = pozycja.label) },
                label = { Text(pozycja.label) }
            )
        }
    }
}
