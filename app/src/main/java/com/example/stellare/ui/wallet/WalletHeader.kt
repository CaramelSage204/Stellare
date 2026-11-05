package com.example.stellare.ui.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.stellare.data.model.UserModel
import com.example.stellare.ui.theme.coinGold

@Composable
internal fun WalletTopBar(
    currentUser: UserModel?,
    onOpenProfileSheet: () -> Unit,
    onToggleDetails: () -> Unit
) {
    val bgCard = MaterialTheme.colorScheme.surfaceVariant
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.align(Alignment.CenterStart),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onOpenProfileSheet) {
                val profileImageUri = currentUser?.profileImageUri
                if (!profileImageUri.isNullOrEmpty()) {
                    AsyncImage(
                        model = profileImageUri,
                        contentDescription = "Avatar",
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .border(1.dp, accent, CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(bgCard),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = currentUser?.firstName?.firstOrNull()?.toString()?.uppercase() ?: "?",
                            color = accent,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Text(
            text = "Portfel",
            color = textPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            modifier = Modifier.align(Alignment.Center)
        )

        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onToggleDetails) {
                Icon(
                    imageVector = Icons.Default.MoreHoriz,
                    contentDescription = "Więcej",
                    tint = textPrimary
                )
            }
        }
    }
}

@Composable
internal fun WalletBalance(balance: Int) {
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val coinGold = MaterialTheme.coinGold


    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "$balance",
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
    }
}

@Composable
internal fun WalletTopUpAction(onTopUp: () -> Unit) {
    val bgCard = MaterialTheme.colorScheme.surfaceVariant
    val textPrimary = MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.clickable { onTopUp() }
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
            Text("Doładuj", color = textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}
