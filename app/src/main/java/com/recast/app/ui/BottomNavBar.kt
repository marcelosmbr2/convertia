package com.recast.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.recast.app.R
import com.recast.app.ui.theme.BaseTextStyle
import com.recast.app.ui.theme.CvIcon
import com.recast.app.ui.theme.Palette

/** Barra inferior do mockup: 80 dp, ícone 22 dp sem indicador preenchido, aba ativa em laranja. */
@Composable
fun BottomNavBar(
    current: Tab,
    queueBadge: Int,
    onSelect: (Tab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Palette.Card)
            .drawBehind { drawLine(Palette.DividerStrong, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx()) }
            .navigationBarsPadding()
            .height(80.dp)
            .selectableGroup(),
    ) {
        Tab.entries.forEach { tab ->
            val selected = tab == current
            val color = if (selected) Palette.AccentText else Palette.TextSecondary
            val badge = if (tab == Tab.QUEUE) queueBadge else 0
            val badgeDescription = if (badge > 0) pluralStringResource(R.plurals.nav_queue_badge, badge, badge) else null
            val label = stringResource(tab.label)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(tab) })
                    .semantics { contentDescription = listOfNotNull(label, badgeDescription).joinToString(", ") },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
            ) {
                Box(Modifier.size(width = 56.dp, height = 32.dp).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
                    CvIcon(tab.icon, contentDescription = null, size = 22.dp, tint = color)
                    if (badge > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .offset(x = 32.dp)
                                .heightIn(min = 18.dp)
                                .widthIn(min = 18.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(Palette.TextPrimary)
                                .border(2.dp, Palette.Card, RoundedCornerShape(9.dp))
                                .padding(horizontal = 5.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = badge.toString(),
                                style = BaseTextStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Palette.Background),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
                Text(
                    text = label,
                    style = BaseTextStyle.copy(
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = color,
                    ),
                    maxLines = 1,
                    modifier = Modifier.clearAndSetSemantics {},
                )
            }
        }
    }
}
