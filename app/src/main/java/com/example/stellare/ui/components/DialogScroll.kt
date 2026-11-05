package com.example.stellare.ui.components

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun Modifier.dialogTextScroll(): Modifier = verticalScroll(rememberScrollState())
