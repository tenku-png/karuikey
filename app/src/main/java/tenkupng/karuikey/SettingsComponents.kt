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
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.foundation.background
import androidx.compose.ui.input.nestedscroll.nestedScroll
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScaffold(
    title: String,
    isHome: Boolean,
    onBack: () -> Unit,
    content: @Composable (Modifier) -> Unit
) {
    if (!isHome) BackHandler(onBack = onBack)
    val surfaces = karuikeySettingsSurfacePalette()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(
                        if (isHome) "Karuikey Keyboard" else title,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    if (!isHome) {
                        IconButton(onClick = onBack) {
                            MaterialSymbolIcon(KaruikeySymbol.ARROW_BACK, "Back", size = 24.sp)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = surfaces.pageBackground,
                    scrolledContainerColor = surfaces.pageBackground
                ),
                scrollBehavior = scrollBehavior
            )
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
        shape = shape ?: MaterialTheme.shapes.large,
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
                    enabled = enabled,
                    thumbContent = if (checked) {
                        {
                            Icon(
                                painterResource(R.drawable.ic_settings_check),
                                contentDescription = null,
                                modifier = Modifier.size(SwitchDefaults.IconSize)
                            )
                        }
                    } else null
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
    // Expressive lists separate rows with a thin page-colored gap instead of a hairline.
    Spacer(
        Modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(karuikeySettingsSurfacePalette().pageBackground)
    )
}

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun <T> ChoiceButtonGroup(options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit) {
    val view = LocalView.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
    ) {
        options.forEachIndexed { index, (label, value) ->
            ToggleButton(
                checked = value == selected,
                onCheckedChange = {
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    onSelect(value)
                },
                modifier = Modifier
                    .weight(1f)
                    .semantics { role = Role.RadioButton },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                }
            ) {
                Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
