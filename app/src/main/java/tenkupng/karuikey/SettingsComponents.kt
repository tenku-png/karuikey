package tenkupng.karuikey

import android.view.HapticFeedbackConstants
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
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.ButtonGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.toShape
import androidx.compose.material3.MaterialShapes
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.delay
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.Animatable
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
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Slider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind

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

// Space pages leave at the bottom so their last rows can scroll clear of the floating bar.
val LocalFloatingBarSpace = compositionLocalOf { 0.dp }
val FLOATING_BAR_SPACE = 96.dp
private val MAX_CONTENT_WIDTH = 720.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScaffold(
    title: String,
    showBack: Boolean,
    onBack: () -> Unit,
    content: @Composable (Modifier) -> Unit
) {
    val surfaces = karuikeySettingsSurfacePalette()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Top + WindowInsetsSides.Horizontal
        ),
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(
                        title,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    if (showBack) {
                        FilledTonalIconButton(
                            onClick = onBack,
                            shapes = IconButtonDefaults.shapes(),
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
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

data class FloatingNavItem(val page: SettingsPage, val icon: KaruikeySymbol, val label: String)

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun FloatingNavBar(
    items: List<FloatingNavItem>,
    selected: SettingsPage,
    onSelect: (SettingsPage) -> Unit
) {
    // Floating pill: the selected destination grows into a labelled capsule, the rest stay icons.
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 8.dp,
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .selectableGroup()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                FloatingNavButton(item, item.page == selected) { onSelect(item.page) }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun FloatingNavButton(item: FloatingNavItem, selected: Boolean, onClick: () -> Unit) {
    val view = LocalView.current
    val motion = MaterialTheme.motionScheme
    val colors = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val container by animateColorAsState(
        if (selected) colors.primary else Color.Transparent,
        motion.defaultEffectsSpec(), label = "nav container"
    )
    val content by animateColorAsState(
        if (selected) colors.onPrimary else colors.onSurfaceVariant,
        motion.defaultEffectsSpec(), label = "nav content"
    )
    val scale by animateFloatAsState(
        if (pressed) 0.9f else 1f, motion.fastSpatialSpec(), label = "nav press"
    )
    Row(
        modifier = Modifier
            .height(56.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .drawBehind { drawRect(container) }
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                role = Role.Tab
            ) {
                if (!selected) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                onClick()
            }
            .animateContentSize(motion.defaultSpatialSpec())
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MaterialSymbolIcon(
            item.icon,
            contentDescription = if (selected) null else item.label,
            tint = content,
            filled = selected
        )
        if (selected) {
            Text(
                item.label,
                color = content,
                style = MaterialTheme.typography.labelLargeEmphasized,
                maxLines = 1,
                modifier = Modifier.padding(start = 10.dp)
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun HeroCard(
    icon: KaruikeySymbol,
    title: String,
    summary: String,
    action: String?,
    enterIndex: Int = 0,
    onAction: () -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme
    val ready = action == null
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .riseIn(enterIndex),
        shape = MaterialTheme.shapes.extraLargeIncreased,
        color = if (ready) colors.primaryContainer else colors.tertiaryContainer,
        contentColor = if (ready) colors.onPrimaryContainer else colors.onTertiaryContainer
    ) {
        Column(
            Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BreathingShape(icon, if (ready) colors.primary else colors.tertiary,
                if (ready) colors.onPrimary else colors.onTertiary)
            Text(title, style = MaterialTheme.typography.headlineMediumEmphasized)
            Text(summary, style = MaterialTheme.typography.bodyLarge)
            if (action != null) {
                Button(
                    onClick = onAction,
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.tertiary, contentColor = colors.onTertiary
                    ),
                    modifier = Modifier.height(56.dp)
                ) {
                    Text(action, style = MaterialTheme.typography.titleMediumEmphasized)
                    MaterialSymbolIcon(KaruikeySymbol.ARROW_FORWARD, tint = colors.onTertiary,
                        modifier = Modifier.padding(start = 8.dp).size(20.dp), size = 20.sp)
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun BreathingShape(icon: KaruikeySymbol, container: Color, content: Color) {
    // Scalloped badge settles with one spring turn. A looping spin would force a full-screen
    // redraw every frame and starve the buffer queue, which stalls page transitions.
    val motion = MaterialTheme.motionScheme
    val turn = remember { Animatable(-120f) }
    LaunchedEffect(Unit) { turn.animateTo(0f, motion.slowSpatialSpec()) }
    val shape = MaterialShapes.Cookie12Sided.toShape()
    Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { rotationZ = turn.value }
                .background(container, shape)
        )
        MaterialSymbolIcon(icon, tint = content, filled = true, size = 32.sp,
            modifier = Modifier.size(32.dp))
    }
}

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun QuickTile(
    icon: KaruikeySymbol,
    title: String,
    summary: String,
    modifier: Modifier = Modifier,
    enterIndex: Int = 0,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val radius by animateDpAsState(
        if (pressed) 16.dp else 32.dp, motion.fastSpatialSpec(), label = "tile shape"
    )
    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = modifier
            .heightIn(min = 140.dp)
            .riseIn(enterIndex),
        shape = RoundedCornerShape(radius),
        color = colors.surfaceContainerLow
    ) {
        Column(
            Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            RowIcon(icon, pressed)
            Spacer(Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.titleMediumEmphasized,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(summary, style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun SettingsSliderRow(
    icon: KaruikeySymbol,
    title: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
    onValueChange: (Float) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RowIcon(icon, false)
            Text(title, style = MaterialTheme.typography.titleMedium,
                color = if (enabled) colors.onSurface else colors.onSurface.copy(alpha = 0.38f),
                modifier = Modifier.weight(1f).padding(start = 16.dp))
            Surface(shape = CircleShape, color = colors.secondaryContainer) {
                Text(valueLabel, style = MaterialTheme.typography.labelLarge,
                    color = colors.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
            }
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            steps = steps,
            enabled = enabled,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

// Page content rises into place on first composition, staggered by index.
@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun Modifier.riseIn(index: Int): Modifier {
    val motion = MaterialTheme.motionScheme
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * 35L)
        enter.animateTo(1f, motion.defaultSpatialSpec())
    }
    val rise = with(LocalDensity.current) { 24.dp.toPx() }
    return graphicsLayer {
        alpha = enter.value.coerceIn(0f, 1f)
        translationY = (1f - enter.value) * rise
    }
}

@Composable
fun SettingsGroup(
    shape: Shape? = null,
    modifier: Modifier = Modifier,
    enterIndex: Int = 0,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .riseIn(enterIndex),
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
    ExpressivePressSurface(onClick = onClick) { pressed ->
        ListItem(
            headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) },
            supportingContent = {
                Text(summary, color = surfaces.supportingText, maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
            },
            leadingContent = { RowIcon(icon, pressed) },
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
    }) { pressed ->
        ListItem(
            headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) },
            supportingContent = {
                Text(summary, color = surfaces.supportingText,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            },
            leadingContent = { RowIcon(icon, pressed) },
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
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun SectionLabel(text: String, enterIndex: Int = 0) {
    // Wide, heavy primary-tinted header that pairs with the page title.
    Text(
        text,
        style = MaterialTheme.typography.titleMediumEmphasized,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(start = 8.dp, top = 12.dp)
            .riseIn(enterIndex)
    )
}

@Composable
fun PageColumn(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    // Content is capped and centred so tablets and landscape get a readable column.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentWidth()
            .widthIn(max = MAX_CONTENT_WIDTH)
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(
                start = KaruikeySettingsTokens.pageHorizontal,
                end = KaruikeySettingsTokens.pageHorizontal,
                top = KaruikeySettingsTokens.pageVertical,
                bottom = KaruikeySettingsTokens.pageVertical + LocalFloatingBarSpace.current
            ),
        verticalArrangement = Arrangement.spacedBy(KaruikeySettingsTokens.groupGap),
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
fun <T> ChoiceButtonGroup(
    options: List<Pair<String, T>>,
    selected: T,
    icons: List<KaruikeySymbol>? = null,
    onSelect: (T) -> Unit
) {
    val view = LocalView.current
    // ButtonGroup widens the pressed button and squeezes its neighbours with a spring.
    ButtonGroup(
        overflowIndicator = { },
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).riseIn(0),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
    ) {
        options.forEachIndexed { index, (label, value) ->
            customItem(
                buttonGroupContent = {
                    val interactionSource = remember { MutableInteractionSource() }
                    ToggleButton(
                        checked = value == selected,
                        onCheckedChange = {
                            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                            onSelect(value)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .animateWidth(interactionSource)
                            .semantics { role = Role.RadioButton },
                        interactionSource = interactionSource,
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        shapes = when (index) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        }
                    ) {
                        icons?.getOrNull(index)?.let { icon ->
                            MaterialSymbolIcon(
                                icon,
                                tint = LocalContentColor.current,
                                filled = value == selected,
                                size = 18.sp,
                                modifier = Modifier.padding(end = 6.dp).size(18.dp)
                            )
                        }
                        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                menuContent = { }
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal fun RowIcon(icon: KaruikeySymbol, pressed: Boolean) {
    // Cookie-shaped tonal container that spins and swells while its row is pressed.
    val motion = MaterialTheme.motionScheme
    val colors = MaterialTheme.colorScheme
    val shape = MaterialShapes.Cookie9Sided.toShape()
    val spin by animateFloatAsState(
        if (pressed) 45f else 0f,
        animationSpec = motion.defaultSpatialSpec(),
        label = "row icon spin"
    )
    val swell by animateFloatAsState(
        if (pressed) 1.08f else 1f,
        animationSpec = motion.fastSpatialSpec(),
        label = "row icon swell"
    )
    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    rotationZ = spin
                    scaleX = swell
                    scaleY = swell
                }
                .background(colors.secondaryContainer, shape)
        )
        MaterialSymbolIcon(
            icon,
            tint = colors.onSecondaryContainer,
            modifier = Modifier.size(KaruikeySettingsTokens.rowIconSize)
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun ExpressivePressSurface(
    enabled: Boolean = true,
    restingColor: Color = Color.Transparent,
    onClick: () -> Unit,
    content: @Composable (pressed: Boolean) -> Unit
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
    ) { content(pressed) }
}

@Composable
fun SpacerHeight(height: Int) {
    Spacer(Modifier.height(height.dp))
}
