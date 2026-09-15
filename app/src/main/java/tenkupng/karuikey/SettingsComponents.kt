package tenkupng.karuikey

import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.sp

object KaruikeySettingsTokens {
    val pageHorizontal = 20.dp
    val pageVertical = 16.dp
    val sectionGap = 20.dp
    val groupGap = 12.dp
    val rowIconSize = 24.dp
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScaffold(
    title: String,
    isHome: Boolean,
    onBack: () -> Unit,
    content: @Composable (Modifier) -> Unit
) {
    if (!isHome) BackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            if (isHome) {
                TopAppBar(
                    title = { Text("Karuikey") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            } else {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            MaterialSymbolIcon(KaruikeySymbol.ARROW_BACK, "Back", size = 24.sp)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding -> content(Modifier.padding(padding)) }
}

@Composable
fun SettingsGroup(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 1.dp
    ) {
        Column { content() }
    }
}

@Composable
fun SettingsHero(title: String, summary: String, icon: KaruikeySymbol) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            MaterialSymbolIcon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(32.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(
                    summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
fun SettingsRow(
    icon: KaruikeySymbol,
    title: String,
    summary: String,
    onClick: () -> Unit
) {
    ExpressivePressSurface(onClick = onClick) {
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = {
                Text(summary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            },
            leadingContent = {
                MaterialSymbolIcon(
                    icon,
                    modifier = Modifier.size(KaruikeySettingsTokens.rowIconSize)
                )
            },
            trailingContent = {
                MaterialSymbolIcon(
                    KaruikeySymbol.CHEVRON_RIGHT,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    size = 20.sp
                )
            },
            colors = ListItemDefaults.colors(
                containerColor = androidx.compose.ui.graphics.Color.Transparent
            )
        )
    }
}

@Composable
fun SettingsSwitchRow(
    icon: KaruikeySymbol,
    title: String,
    summary: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    val view = LocalView.current
    ExpressivePressSurface(enabled = enabled, onClick = {
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        onCheckedChange(!checked)
    }) {
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = { Text(summary, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            leadingContent = {
                MaterialSymbolIcon(icon, modifier = Modifier.size(KaruikeySettingsTokens.rowIconSize))
            },
            trailingContent = {
                Switch(
                    checked = checked,
                    onCheckedChange = {
                        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                        onCheckedChange(it)
                    },
                    enabled = enabled
                )
            },
            colors = ListItemDefaults.colors(
                containerColor = androidx.compose.ui.graphics.Color.Transparent
            )
        )
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

@Composable
fun PageColumn(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = KaruikeySettingsTokens.pageHorizontal,
                vertical = KaruikeySettingsTokens.pageVertical
            ),
        verticalArrangement = Arrangement.spacedBy(KaruikeySettingsTokens.sectionGap),
        content = content
    )
}

@Composable
fun GroupDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = KaruikeySettingsTokens.pageHorizontal),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    )
}

@Composable
fun ChoiceRow(title: String, selected: Boolean, onClick: () -> Unit) {
    val view = LocalView.current
    ExpressivePressSurface(onClick = {
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        onClick()
    }) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = KaruikeySettingsTokens.pageHorizontal, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            if (selected) {
                Text("✓", color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

@Composable
private fun ExpressivePressSurface(
    enabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.985f else 1f, label = "row press scale")
    val radius by animateDpAsState(if (pressed) 20.dp else 4.dp, label = "row press shape")
    val color by animateColorAsState(
        if (pressed) MaterialTheme.colorScheme.surfaceContainerHighest
        else androidx.compose.ui.graphics.Color.Transparent,
        label = "row press tone"
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .indication(interactionSource, LocalIndication.current)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            ),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(radius),
        color = color
    ) { content() }
}

@Composable
fun SpacerHeight(height: Int) {
    Spacer(Modifier.height(height.dp))
}
