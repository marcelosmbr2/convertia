package com.convertia.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.convertia.app.R
import com.convertia.app.ui.components.PrimaryButton
import com.convertia.app.ui.theme.Palette
import com.convertia.app.ui.theme.TextStyles

/** Provisório: o onboarding do mockup entra numa etapa própria. */
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Palette.Background)
            .systemBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp, androidx.compose.ui.Alignment.Bottom),
    ) {
        Text(stringResource(R.string.app_name), style = TextStyles.ScreenTitle)
        PrimaryButton(stringResource(R.string.onboarding_start), onClick = onFinish, modifier = Modifier.fillMaxWidth())
    }
}
