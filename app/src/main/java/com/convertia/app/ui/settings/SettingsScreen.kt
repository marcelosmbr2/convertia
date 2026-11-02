package com.convertia.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.convertia.app.BuildConfig
import com.convertia.app.R
import com.convertia.app.data.SampleData
import com.convertia.app.domain.AppSettings
import com.convertia.app.domain.ConflictPolicy
import com.convertia.app.domain.Preset
import com.convertia.app.ui.components.IconCircleButton
import com.convertia.app.ui.components.TextPillButton
import com.convertia.app.ui.components.ListCard
import com.convertia.app.ui.components.RowDivider
import com.convertia.app.ui.components.ScreenHeader
import com.convertia.app.ui.components.SectionLabel
import com.convertia.app.ui.components.SegmentedControl
import com.convertia.app.ui.components.SmallOutlinedButton
import com.convertia.app.ui.components.ToggleRow
import com.convertia.app.ui.theme.BaseTextStyle
import com.convertia.app.ui.theme.ConvertiaIcons
import com.convertia.app.ui.theme.ConvertiaTheme
import com.convertia.app.ui.theme.CvIcon
import com.convertia.app.ui.theme.MonoTextStyle
import com.convertia.app.ui.theme.Palette
import com.convertia.app.ui.theme.TextStyles

@Composable
fun SettingsScreen(
    onOpenPresets: () -> Unit,
    onRestartOnboarding: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val engineVersion by viewModel.ytDlpVersion.collectAsStateWithLifecycle()
    val checking by viewModel.checking.collectAsStateWithLifecycle()
    SettingsContent(
        settings = settings,
        presetCount = viewModel.presets.size,
        engineVersion = engineVersion,
        checkingEngine = checking,
        onUpdate = viewModel::update,
        onCheckEngine = viewModel::checkEngineUpdates,
        onOpenPresets = onOpenPresets,
        onRestartOnboarding = onRestartOnboarding,
    )
}

@Composable
fun SettingsContent(
    settings: AppSettings,
    presetCount: Int,
    engineVersion: String?,
    checkingEngine: Boolean,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onCheckEngine: () -> Unit,
    onOpenPresets: () -> Unit,
    onRestartOnboarding: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        ScreenHeader(stringResource(R.string.settings_title), stringResource(R.string.settings_subtitle))
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            PrivacyCard()

            Section(R.string.settings_section_general) {
                InfoRow(stringResource(R.string.settings_save_folder), stringResource(R.string.download_folder), mono = true)
                RowDivider()
                ToggleRow(
                    title = stringResource(R.string.settings_detect_links),
                    description = stringResource(R.string.settings_detect_links_desc),
                    checked = settings.detectClipboardLinks,
                    onCheckedChange = { on -> onUpdate { it.copy(detectClipboardLinks = on) } },
                )
                RowDivider()
                ToggleRow(
                    title = stringResource(R.string.settings_notifications),
                    description = stringResource(R.string.settings_notifications_desc),
                    checked = settings.notifications,
                    onCheckedChange = { on -> onUpdate { it.copy(notifications = on) } },
                )
            }

            Section(R.string.settings_section_downloads) {
                InfoRow(stringResource(R.string.settings_preferred_quality), "1080p · MP4")
                RowDivider()
                ChoiceRow(
                    title = stringResource(R.string.settings_concurrent_downloads),
                    options = AppSettings.concurrentDownloadChoices,
                    selected = settings.concurrentDownloads,
                    label = { it.toString() },
                    onSelect = { n -> onUpdate { it.copy(concurrentDownloads = n) } },
                )
                RowDivider()
                ToggleRow(
                    title = stringResource(R.string.settings_wifi_only),
                    description = stringResource(R.string.settings_wifi_only_desc),
                    checked = settings.wifiOnly,
                    onCheckedChange = { on -> onUpdate { it.copy(wifiOnly = on) } },
                )
                RowDivider()
                InfoRow(stringResource(R.string.settings_name_template), "%(title)s.%(ext)s", mono = true)
            }

            Section(R.string.settings_section_conversion) {
                NavigationRow(
                    title = stringResource(R.string.settings_presets),
                    subtitle = pluralStringResource(R.plurals.settings_preset_count, presetCount, presetCount),
                    onClick = onOpenPresets,
                )
                RowDivider()
                ChoiceRow(
                    title = stringResource(R.string.settings_conflict),
                    options = ConflictPolicy.entries,
                    selected = settings.conflictPolicy,
                    label = {
                        stringResource(
                            when (it) {
                                ConflictPolicy.RENAME -> R.string.settings_conflict_rename
                                ConflictPolicy.REPLACE -> R.string.settings_conflict_replace
                                ConflictPolicy.SKIP -> R.string.settings_conflict_skip
                            },
                        )
                    },
                    onSelect = { policy -> onUpdate { it.copy(conflictPolicy = policy) } },
                )
                RowDivider()
                ChoiceRow(
                    title = stringResource(R.string.settings_parallel_conversions),
                    options = AppSettings.parallelConversionChoices,
                    selected = settings.parallelConversions,
                    label = { it.toString() },
                    onSelect = { n -> onUpdate { it.copy(parallelConversions = n) } },
                )
                RowDivider()
                ToggleRow(
                    title = stringResource(R.string.settings_hardware_acceleration),
                    description = stringResource(R.string.settings_hardware_acceleration_desc),
                    checked = settings.hardwareAcceleration,
                    onCheckedChange = { on -> onUpdate { it.copy(hardwareAcceleration = on) } },
                )
                RowDivider()
                ToggleRow(
                    title = stringResource(R.string.settings_keep_originals),
                    description = stringResource(R.string.settings_keep_originals_desc),
                    checked = settings.keepOriginals,
                    onCheckedChange = { on -> onUpdate { it.copy(keepOriginals = on) } },
                )
            }

            Section(R.string.settings_section_engine) {
                SettingRow(title = "yt-dlp", subtitle = engineVersion ?: stringResource(R.string.settings_engine_preparing), mono = true) {
                    SmallOutlinedButton(
                        stringResource(if (checkingEngine) R.string.settings_checking else R.string.settings_check),
                        onClick = onCheckEngine,
                    )
                }
                RowDivider()
                SettingRow(
                    title = "LibreOffice",
                    subtitle = if (BuildConfig.LIBREOFFICE_VERSION.isEmpty()) stringResource(R.string.settings_libreoffice_missing) else stringResource(R.string.settings_libreoffice_included, BuildConfig.LIBREOFFICE_VERSION),
                    mono = true,
                ) {
                    val uriHandler = LocalUriHandler.current
                    TextPillButton(
                        text = stringResource(R.string.settings_license),
                        onClick = { uriHandler.openUri("https://www.libreoffice.org/about-us/licenses/") },
                        color = Palette.TextSecondary,
                        fontWeight = FontWeight.Medium,
                    )
                }
                SettingRow(title = "ffmpeg", subtitle = stringResource(R.string.settings_ffmpeg_included), mono = true) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(Palette.TextSecondary))
                        Text(stringResource(R.string.settings_ready), style = BaseTextStyle.copy(fontSize = 12.5.sp, color = Palette.TextSecondary))
                    }
                }
            }

            Section(R.string.settings_section_other) {
                NavigationRow(title = stringResource(R.string.settings_review_intro), onClick = onRestartOnboarding, minHeight = 56)
            }

            Text(
                stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                style = MonoTextStyle.copy(fontSize = 11.5.sp, color = Palette.TextMuted),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PrivacyCard() {
    ListCard {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CvIcon(ConvertiaIcons.Lock, contentDescription = null, size = 22.dp, tint = Palette.TextSecondary, strokeWidth = 1.7f)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(stringResource(R.string.settings_privacy_title), style = BaseTextStyle.copy(fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold))
                Text(
                    stringResource(R.string.settings_privacy_body),
                    style = BaseTextStyle.copy(fontSize = 12.5.sp, lineHeight = 18.sp, color = Palette.TextSecondary),
                )
            }
            Text("0 B", style = MonoTextStyle.copy(fontSize = 11.sp, color = Palette.TextSecondary))
        }
    }
}

@Composable
private fun Section(title: Int, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(stringResource(title))
        ListCard(content = content)
    }
}

/** Linha de leitura (ainda sem ação nesta fase). */
@Composable
private fun InfoRow(title: String, subtitle: String, mono: Boolean = false) {
    SettingRow(title = title, subtitle = subtitle, mono = mono)
}

@Composable
private fun NavigationRow(title: String, onClick: () -> Unit, subtitle: String? = null, minHeight: Int = 64) {
    SettingRow(title = title, subtitle = subtitle, onClick = onClick, minHeight = minHeight) {
        CvIcon(ConvertiaIcons.ChevronRight, contentDescription = null, size = 18.dp, tint = Palette.TextMeta2, strokeWidth = 2f)
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String?,
    mono: Boolean = false,
    onClick: (() -> Unit)? = null,
    minHeight: Int = 64,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = minHeight.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = TextStyles.RowTitle)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = if (mono) MonoTextStyle.copy(fontSize = 12.sp, color = Palette.TextMeta)
                    else BaseTextStyle.copy(fontSize = 12.5.sp, color = Palette.TextTertiary),
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
private fun <T> ChoiceRow(title: String, options: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    Column(
        Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, style = TextStyles.RowTitle)
        SegmentedControl(options = options, selected = selected, onSelect = onSelect, label = label)
    }
}

// Predefinições -----------------------------------------------------------------------------

@Composable
fun PresetsScreen(presets: List<Preset>, onBack: () -> Unit, onApply: (Preset) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconCircleButton(
                icon = ConvertiaIcons.ArrowLeft,
                contentDescription = stringResource(R.string.action_back),
                onClick = onBack,
                size = 48.dp,
                iconSize = 22.dp,
                tint = Palette.TextPrimary,
            )
            Text(
                stringResource(R.string.presets_title),
                style = BaseTextStyle.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
                modifier = Modifier.semantics { heading() },
            )
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.presets_intro),
                style = BaseTextStyle.copy(fontSize = 13.sp, lineHeight = 19.5.sp, color = Palette.TextTertiary),
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 6.dp),
            )
            presets.forEach { preset ->
                ListCard {
                    Row(
                        Modifier.padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(preset.name, style = TextStyles.RowTitle)
                            Text(preset.detail, style = BaseTextStyle.copy(fontSize = 12.5.sp, color = Palette.TextTertiary))
                            Text(preset.rule, style = MonoTextStyle.copy(fontSize = 11.5.sp, color = Palette.TextSecondary))
                        }
                        SmallOutlinedButton(stringResource(R.string.presets_apply), onClick = { onApply(preset) })
                    }
                }
            }
        }
    }
}

@Preview(widthDp = 396, heightDp = 1600, backgroundColor = 0xFF0F0F11, showBackground = true)
@Composable
private fun SettingsPreview() {
    ConvertiaTheme {
        SettingsContent(AppSettings(), 5, "2025.11.12", false, {}, {}, {}, {})
    }
}

@Preview(widthDp = 396, heightDp = 812, backgroundColor = 0xFF0F0F11, showBackground = true)
@Composable
private fun PresetsPreview() {
    ConvertiaTheme {
        PresetsScreen(SampleData.presets, {}, {})
    }
}
