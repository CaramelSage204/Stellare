package com.example.stellare.ui.session

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.stellare.data.model.UserRole

@Composable
internal fun RegistrationRoleStep(
    role: UserRole,
    onRoleChange: (UserRole) -> Unit
) {
    Text("Kim jesteś w aplikacji Stellare?", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
    Text("Wybierz swoją rolę, aby dostosować funkcjonalności.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f), modifier = Modifier.padding(bottom = 24.dp))

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RoleOptionCard(
            selected = role == UserRole.PATIENT,
            onSelect = { onRoleChange(UserRole.PATIENT) },
            icon = Icons.Default.Person,
            iconDescription = "Pacjent",
            iconBackground = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
            iconTint = MaterialTheme.colorScheme.primary,
            title = "Pacjent",
            subtitle = "Szukam profesjonalnego wsparcia i rozmowy"
        )
        RoleOptionCard(
            selected = role == UserRole.PSYCHOLOGIST,
            onSelect = { onRoleChange(UserRole.PSYCHOLOGIST) },
            icon = Icons.Default.School,
            iconDescription = "Psycholog",
            iconBackground = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
            iconTint = MaterialTheme.colorScheme.secondary,
            title = "Dyplomowany Psycholog",
            subtitle = "Chcę oferować sesje, doradzać i wspierać"
        )
        RoleOptionCard(
            selected = role == UserRole.PSYCHOLOGY_STUDENT,
            onSelect = { onRoleChange(UserRole.PSYCHOLOGY_STUDENT) },
            icon = Icons.Default.School,
            iconDescription = "Student",
            iconBackground = MaterialTheme.colorScheme.tertiaryContainer,
            iconTint = MaterialTheme.colorScheme.tertiary,
            title = "Student Psychologii",
            subtitle = "Chcę zdobywać praktykę i doświadczenie"
        )
    }
}

@Composable
private fun RoleOptionCard(
    selected: Boolean,
    onSelect: () -> Unit,
    icon: ImageVector,
    iconDescription: String,
    iconBackground: Color,
    iconTint: Color,
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                shape = RoundedCornerShape(16.dp)
            )
            .background(
                color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onSelect() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(iconBackground),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, iconDescription, tint = iconTint, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
        RadioButton(selected = selected, onClick = onSelect)
    }
}
