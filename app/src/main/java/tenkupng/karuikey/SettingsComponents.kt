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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.sp

object KaruikeySettingsTokens {
    val pageHorizontal = 20.dp
    val pageVertical = 20.dp
    val sectionGap = 24.dp
    val groupGap = 14.dp
    val rowIconSize = 24.dp
}

data class SettingsSurfacePalette(
    val pageBackground: Color,
    val sectionContainer: Color,
    val interactiveContainer: Color,
    val selectedContainer: Color,
    val supportingText: Color,
    val iconTint: Color,
    val divider: Color
)

@Composable
fun karuikeySettingsSurfacePalette(): SettingsSurfacePalette {
    val colors = MaterialTheme.colorScheme
    return SettingsSurfacePalette(
        pageBackground = colors.background,
        sectionContainer = colors.surfaceContainerLow,
        interactiveContainer = colors.surfaceContainerHighest,
        selectedContainer = colors.secondaryContainer,
        supportingText = colors.onSurfaceVariant,
        iconTint = colors.onSurfaceVariant,
        divider = colors.outlineVariant
    )
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
    val surfaces = karuikeySettingsSurfacePalette()
    Scaffold(
        topBar = {
            if (isHome) {
                TopAppBar(
                    title = {
                        Text(
                            "Karuikey Keyboard",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = surfaces.pageBackground
                    )
                )
            } else {
                TopAppBar(
                    title = { Text(title, style = MaterialTheme.typography.titleLarge) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            MaterialSymbolIcon(KaruikeySymbol.ARROW_BACK, "Back", size = 24.sp)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = surfaces.pageBackground
                    )
                )
            }
        },
        containerColor = surfaces.pageBackground
    ) { padding -> content(Modifier.padding(padding)) }
}

@Composable
fun SettingsGroup(
    shape: Shape? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape ?: MaterialTheme.shapes.medium,
        color = karuikeySettingsSurfacePalette().sectionContainer,
        tonalElevation = 0.dp
    ) {
        Column { content() }
    }
}

@Composable
fun SettingsRow(
    icon: KaruikeySymbol,
    title: String,
    summary: String,
    onClick: () -> Unit
) {
    val surfaces = karuikeySettingsSurfacePalette()
    ExpressivePressSurface(onClick = onClick) {
        ListItem(
            headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) },
            supportingContent = {
                Text(summary, color = surfaces.supportingText, maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
            },
            leadingContent = {
                MaterialSymbolIcon(
                    icon,
                    tint = surfaces.iconTint,
                    modifier = Modifier.size(KaruikeySettingsTokens.rowIconSize)
                )
            },
            trailingContent = {
                MaterialSymbolIcon(
                    KaruikeySymbol.CHEVRON_RIGHT,
                    tint = surfaces.iconTint,
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
    val surfaces = karuikeySettingsSurfacePalette()
    ExpressivePressSurface(enabled = enabled, onClick = {
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        onCheckedChange(!checked)
    }) {
        ListItem(
            headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) },
            supportingContent = {
                Text(summary, color = surfaces.supportingText,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            },
            leadingContent = {
                MaterialSymbolIcon(icon, tint = surfaces.iconTint,
                    modifier = Modifier.size(KaruikeySettingsTokens.rowIconSize))
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
        style = MaterialTheme.typography.titleLarge,
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
    val surfaces = karuikeySettingsSurfacePalette()
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = KaruikeySettingsTokens.pageHorizontal),
        color = surfaces.divider
    )
}

@Composable
fun ChoiceRow(title: String, selected: Boolean, onClick: () -> Unit) {
    val view = LocalView.current
    val surfaces = karuikeySettingsSurfacePalette()
    ExpressivePressSurface(
        restingColor = if (selected) surfaces.selectedContainer else Color.Transparent,
        onClick = {
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            onClick()
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = KaruikeySettingsTokens.pageHorizontal, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            if (selected) {
                Icon(
                    painterResource(R.drawable.ic_settings_check),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun ExpressivePressSurface(
    enabled: Boolean = true,
    restingColor: Color = Color.Transparent,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val motion = MaterialTheme.motionScheme
    val surfaces = karuikeySettingsSurfacePalette()
    val scale by animateFloatAsState(
        if (pressed) 0.985f else 1f,
        animationSpec = motion.fastSpatialSpec(),
        label = "row press scale"
    )
    val radius by animateDpAsState(
        if (pressed) 16.dp else 8.dp,
        animationSpec = motion.fastSpatialSpec(),
        label = "row press shape"
    )
    val color by animateColorAsState(
        if (pressed) surfaces.interactiveContainer else restingColor,
        animationSpec = motion.fastEffectsSpec(),
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
