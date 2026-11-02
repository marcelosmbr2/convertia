package com.convertia.app.ui.onboarding

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.convertia.app.R
import com.convertia.app.ui.components.PillButton
import com.convertia.app.ui.components.PrimaryButton
import com.convertia.app.ui.components.RowDivider
import com.convertia.app.ui.components.TextPillButton
import com.convertia.app.ui.theme.BaseTextStyle
import com.convertia.app.ui.theme.ConvertiaIcons
import com.convertia.app.ui.theme.ConvertiaTheme
import com.convertia.app.ui.theme.CvIcon
import com.convertia.app.ui.theme.CvIconSpec
import com.convertia.app.ui.theme.Palette

private const val STEP_COUNT = 3

/** Introdução em três passos (Bem-vindo, Privacidade, Permissões). */
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val context = LocalContext.current
    var step by rememberSaveable { mutableIntStateOf(0) }
    var permissions by remember { mutableStateOf(PermissionState.read(context)) }

    // Volta das telas do sistema (pedido de permissão, ajustes de notificação).
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { permissions = PermissionState.read(context) }

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissions = PermissionState.read(context)
    }
    val storageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissions = PermissionState.read(context)
    }

    BackHandler(enabled = step > 0) { step-- }

    OnboardingContent(
        step = step,
        permissions = permissions,
        onNext = { if (step == STEP_COUNT - 1) onFinish() else step++ },
        onSkip = onFinish,
        onAllowNotifications = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !permissions.askedNotifications(context)) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                markNotificationsAsked(context)
            } else {
                // Negado antes ou Android < 13: só dá para ligar nos ajustes do sistema.
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        },
        onAllowFiles = { storageLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) },
    )
}

/** As duas permissões do passo 3. */
data class PermissionState(val notifications: Boolean, val files: Boolean) {
    fun askedNotifications(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ASKED, false)

    companion object {
        fun read(context: Context) = PermissionState(
            notifications = NotificationManagerCompat.from(context).areNotificationsEnabled(),
            // Do Android 10 em diante, salvar em Download/ não exige permissão.
            files = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
}

private const val PREFS = "onboarding"
private const val KEY_ASKED = "notifications_asked"

private fun markNotificationsAsked(context: Context) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean(KEY_ASKED, true) }
}

@Composable
fun OnboardingContent(
    step: Int,
    permissions: PermissionState,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    onAllowNotifications: () -> Unit,
    onAllowFiles: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Palette.Background)
            .systemBarsPadding()
            .padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 24.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            StepDots(step, Modifier.weight(1f))
            TextPillButton(
                text = stringResource(R.string.onboarding_skip),
                onClick = onSkip,
                color = Palette.TextSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                height = 40.dp,
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (step == 2) 20.dp else 24.dp)) {
            // A ilustração sangra até as bordas (margem −24 do mockup).
            val illustration = Modifier
                .weight(1f)
                .bleed(24.dp)
            when (step) {
                0 -> {
                    WelcomeIllustration(illustration)
                    StepText(R.string.onboarding_welcome_eyebrow, R.string.onboarding_welcome_title, R.string.onboarding_welcome_body)
                }
                1 -> {
                    PrivacyIllustration(illustration)
                    StepText(R.string.onboarding_privacy_eyebrow, R.string.onboarding_privacy_title, R.string.onboarding_privacy_body)
                }
                else -> {
                    PermissionsIllustration(illustration)
                    StepText(R.string.onboarding_permissions_eyebrow, R.string.onboarding_permissions_title, R.string.onboarding_permissions_body)
                    PermissionRows(permissions, onAllowNotifications, onAllowFiles)
                }
            }
        }
        PrimaryButton(
            text = stringResource(if (step == STEP_COUNT - 1) R.string.onboarding_start else R.string.onboarding_continue),
            onClick = onNext,
            height = 52.dp,
            textStyle = BaseTextStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Estende o elemento [amount] para cada lado, além do padding do pai. */
private fun Modifier.bleed(amount: Dp): Modifier = layout { measurable, constraints ->
    val extra = amount.roundToPx() * 2
    val placeable = measurable.measure(
        constraints.copy(
            minWidth = (constraints.minWidth + extra).coerceAtLeast(0),
            maxWidth = constraints.maxWidth + extra,
        ),
    )
    layout(constraints.maxWidth, placeable.height) { placeable.place(-amount.roundToPx(), 0) }
}

@Composable
private fun StepDots(step: Int, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.onboarding_step, step + 1, STEP_COUNT)
    Row(modifier.semantics { contentDescription = description }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(STEP_COUNT) { index ->
            val active = index == step
            val width by animateDpAsState(if (active) 22.dp else 6.dp, tween(200), label = "dot")
            Box(
                Modifier
                    .size(width = width, height = 6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (active) Palette.TextPrimary else Palette.Gray33),
            )
        }
    }
}

@Composable
private fun ColumnScope.StepText(eyebrow: Int, title: Int, body: Int) {
    Column(Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(eyebrow),
            style = BaseTextStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.02.em, color = Palette.AccentText),
        )
        Text(
            stringResource(title),
            style = BaseTextStyle.copy(fontSize = 34.sp, lineHeight = 36.7.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.03).em),
            modifier = Modifier.semantics { heading() },
        )
        Text(
            stringResource(body),
            style = BaseTextStyle.copy(fontSize = 15.sp, lineHeight = 23.sp, color = Palette.TextSecondary),
        )
    }
}

@Composable
private fun PermissionRows(state: PermissionState, onAllowNotifications: () -> Unit, onAllowFiles: () -> Unit) {
    Column(Modifier.padding(bottom = 8.dp)) {
        PermissionRow(
            icon = ConvertiaIcons.Bell,
            title = stringResource(R.string.onboarding_perm_notifications),
            description = stringResource(R.string.onboarding_perm_notifications_desc),
            granted = state.notifications,
            onAllow = onAllowNotifications,
        )
        RowDivider(color = Palette.DividerStrong)
        PermissionRow(
            icon = ConvertiaIcons.Folder,
            title = stringResource(R.string.onboarding_perm_files),
            description = stringResource(R.string.onboarding_perm_files_desc),
            granted = state.files,
            onAllow = onAllowFiles,
        )
    }
}

@Composable
private fun PermissionRow(icon: CvIconSpec, title: String, description: String, granted: Boolean, onAllow: () -> Unit) {
    Row(
        Modifier.padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Palette.Raised),
            contentAlignment = Alignment.Center,
        ) {
            CvIcon(icon, contentDescription = null, size = 19.dp, tint = Palette.TextEmphasis, strokeWidth = 1.7f)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = BaseTextStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
            Text(description, style = BaseTextStyle.copy(fontSize = 13.sp, lineHeight = 18.sp, color = Palette.TextMeta))
        }
        PillButton(
            onClick = onAllow,
            enabled = !granted,
            height = 40.dp,
            background = if (granted) Palette.Background else Palette.TextPrimary,
            pressedBackground = if (granted) Palette.Background else Palette.TextEmphasis,
            border = BorderStroke(1.dp, if (granted) Palette.BorderStrong else Palette.TextPrimary),
            contentColor = if (granted) Palette.TextSecondary else Palette.Background,
            contentPadding = PaddingValues(horizontal = 14.dp),
        ) {
            Text(
                stringResource(if (granted) R.string.onboarding_perm_granted else R.string.onboarding_perm_allow),
                style = BaseTextStyle.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (granted) Palette.TextSecondary else Palette.Background,
                ),
            )
        }
    }
}

@Preview(widthDp = 396, heightDp = 812, backgroundColor = 0xFF0F0F11, showBackground = true)
@Composable
private fun OnboardingWelcomePreview() {
    ConvertiaTheme { OnboardingContent(0, PermissionState(false, true), {}, {}, {}, {}) }
}

@Preview(widthDp = 396, heightDp = 812, backgroundColor = 0xFF0F0F11, showBackground = true)
@Composable
private fun OnboardingPermissionsPreview() {
    ConvertiaTheme { OnboardingContent(2, PermissionState(false, true), {}, {}, {}, {}) }
}
