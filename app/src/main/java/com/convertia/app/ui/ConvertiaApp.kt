package com.convertia.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.convertia.app.ui.theme.AccentBright
import com.convertia.app.ui.theme.ConvertiaTheme

@Composable
fun ConvertiaApp() {
    var current by rememberSaveable { mutableStateOf(Destination.Download) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            ConvertiaNavigationBar(current = current, onSelect = { current = it })
        },
    ) { innerPadding ->
        PlaceholderScreen(
            destination = current,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

@Composable
private fun ConvertiaNavigationBar(
    current: Destination,
    onSelect: (Destination) -> Unit,
) {
    Column {
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 0.dp,
        ) {
            Destination.entries.forEach { destination ->
                val selected = destination == current
                NavigationBarItem(
                    selected = selected,
                    onClick = { onSelect(destination) },
                    // O rótulo já descreve o item para o TalkBack.
                    icon = { Icon(painterResource(destination.icon), contentDescription = null) },
                    label = {
                        Text(
                            text = stringResource(destination.label),
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = AccentBright,
                        selectedTextColor = AccentBright,
                        indicatorColor = Color.Transparent,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
            }
        }
    }
}

/** Tela provisória de cada aba, até as telas de verdade serem implementadas. */
@Composable
private fun PlaceholderScreen(
    destination: Destination,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = stringResource(destination.label),
            style = MaterialTheme.typography.headlineLarge,
        )
        Text(
            text = stringResource(destination.subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(widthDp = 412, heightDp = 892)
@Composable
private fun ConvertiaAppPreview() {
    ConvertiaTheme {
        ConvertiaApp()
    }
}
