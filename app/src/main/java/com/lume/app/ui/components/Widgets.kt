package com.lume.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lume.app.R
import com.lume.app.ui.theme.AppColors
import com.lume.app.ui.theme.Motion

@Composable
fun CoverImage(
    url: String?,
    width: Dp,
    height: Dp,
    radius: Dp = 8.dp,
    @Suppress("UNUSED_PARAMETER") sourceId: String? = null,
) {
    Box(
        modifier = Modifier
            .size(width, height)
            .clip(RoundedCornerShape(radius))
            .background(AppColors.card),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isNullOrBlank()) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                contentDescription = null,
                tint = AppColors.textTertiary,
                modifier = Modifier.size(28.dp),
            )
        } else {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                placeholder = null,
                error = null,
            )
        }
    }
}

@Composable
fun QuickSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    hint: String? = null,
) {
    val resolvedHint = hint ?: stringResource(R.string.search_hint_quick)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(AppColors.search)
            .clickable(enabled = readOnly, role = Role.Button, onClick = onSubmit)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = null,
            tint = AppColors.textSecondary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(
                    text = resolvedHint,
                    color = AppColors.textTertiary,
                    fontSize = 14.sp,
                )
            }
            if (!readOnly) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = TextStyle(color = AppColors.textPrimary, fontSize = 14.sp),
                    cursorBrush = SolidColor(AppColors.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = resolvedHint },
                )
            } else if (value.isNotEmpty()) {
                Text(
                    text = value,
                    color = AppColors.textPrimary,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TitleListRow(
    title: String,
    coverUrl: String?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    typeLabel: String? = null,
    badge: String? = null,
    sourceId: String? = null,
    /** A bar along the left edge, e.g. the library status of the title. */
    marker: Color? = null,
    /** Spoken form of [marker]. */
    markerLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (markerLabel != null) Modifier.semantics { stateDescription = markerLabel } else Modifier)
            .then(
                if (marker != null) {
                    Modifier.drawBehind {
                        val inset = 10.dp.toPx()
                        drawRect(marker, topLeft = Offset(0f, inset), size = Size(4.dp.toPx(), size.height - 2 * inset))
                    }
                } else {
                    Modifier
                },
            )
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                } else {
                    Modifier.clickable(onClick = onClick)
                },
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverImage(url = coverUrl, width = 48.dp, height = 64.dp, radius = 6.dp, sourceId = sourceId)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    color = AppColors.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (!badge.isNullOrBlank()) {
                    Text(
                        text = badge,
                        color = AppColors.onAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(AppColors.accent)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            val line = listOfNotNull(typeLabel, subtitle).filter { it.isNotBlank() }.joinToString(" · ")
            if (line.isNotEmpty()) {
                Text(
                    text = line,
                    color = AppColors.textSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
fun LumeLogo(
    selected: Boolean,
    size: Dp = 28.dp,
    /** Null when the surrounding element already carries the label. */
    contentDescription: String? = stringResource(R.string.app_name),
    /** Draws a thin ring so the dark tile stays visible on a dark bar. */
    ringed: Boolean = false,
) {
    val highlight by animateColorAsState(
        if (selected) AppColors.accent.copy(alpha = 0.2f) else Color.Transparent,
        Motion.short(),
        label = "logo",
    )
    val shape = RoundedCornerShape(size * 0.22f)
    Image(
        painter = painterResource(R.mipmap.ic_launcher_foreground),
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(highlight)
            .then(
                if (ringed) {
                    Modifier.border(1.dp, if (selected) AppColors.accent else AppColors.outline, shape)
                } else {
                    Modifier
                },
            ),
    )
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = AppColors.accent)
    }
}

@Composable
fun ErrorRetryBox(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onCloudflare: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
            color = AppColors.textSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(
                containerColor = AppColors.accent,
                contentColor = AppColors.onAccent,
            ),
        ) {
            Text(stringResource(R.string.action_retry))
        }
        if (onCloudflare != null) {
            TextButton(onClick = onCloudflare) {
                Text("Cloudflare", color = AppColors.accentText)
            }
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    onTap: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onTap != null) Modifier.clickable(role = Role.Button, onClick = onTap) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = AppColors.textPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        trailing?.invoke()
    }
}

/** Poster card used by horizontal rows (home spotlight, global search). */
@Composable
fun CoverCard(
    title: String,
    coverUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null,
    width: Dp = 128.dp,
    /** A bar along the top of the cover, e.g. the library status of the title. */
    marker: Color? = null,
    /** Spoken form of [marker]. */
    markerLabel: String? = null,
) {
    Column(
        modifier = modifier
            .width(width)
            .clip(RoundedCornerShape(10.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .then(if (markerLabel != null) Modifier.semantics { stateDescription = markerLabel } else Modifier),
    ) {
        Box {
            CoverImage(url = coverUrl, width = width, height = width * 1.4f, radius = 10.dp)
            if (marker != null) {
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(marker),
                )
            }
            if (!badge.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(badge, color = Color.White, fontSize = 11.sp, maxLines = 1)
                }
            }
        }
        Text(
            text = title,
            color = AppColors.textPrimary,
            fontSize = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
        )
    }
}

/** What a screen is showing; drives [AnimatedLoadState]. */
enum class LoadState { Loading, Error, Empty, Content }

fun loadStateOf(loading: Boolean, error: String?, isEmpty: Boolean = false): LoadState = when {
    loading -> LoadState.Loading
    error != null -> LoadState.Error
    isEmpty -> LoadState.Empty
    else -> LoadState.Content
}

/**
 * Cross-fades between loading / error / empty / content with the app-wide [Motion] spec, so
 * every screen swaps state the same way instead of popping.
 *
 * During the first [Motion.MEDIUM] ms (while the screen itself is still sliding in) a loading
 * state shows nothing and a change is instant: content that is ready almost at once — cached
 * data, a ViewModel kept across Back — appears without a spinner flash or a second animation.
 */
@Composable
fun AnimatedLoadState(
    state: LoadState,
    modifier: Modifier = Modifier,
    content: @Composable (LoadState) -> Unit,
) {
    var settled by remember { mutableStateOf(state != LoadState.Loading) }
    LaunchedEffect(Unit) {
        delay(Motion.MEDIUM.toLong())
        settled = true
    }
    val swap = Motion.contentSwap<LoadState>()
    AnimatedContent(
        targetState = state,
        modifier = modifier,
        transitionSpec = { if (settled) swap() else EnterTransition.None togetherWith ExitTransition.None },
        label = "loadState",
    ) { target ->
        if (target == LoadState.Loading && !settled) Box(Modifier.fillMaxSize()) else content(target)
    }
}

/** Confirm step for a destructive or hard-to-undo action. */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirmLabel, color = AppColors.danger) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** A password field with a show/hide toggle; IME action and its handler are passed through. */
@Composable
fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Done,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        label = { Text(label) },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        keyboardActions = keyboardActions,
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = stringResource(if (visible) R.string.cd_hide_password else R.string.cd_show_password),
                    tint = AppColors.textSecondary,
                )
            }
        },
        modifier = modifier,
    )
}

/** Small empty-state message, centered. */
@Composable
fun EmptyBox(message: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(message, color = AppColors.textSecondary, fontSize = 15.sp, textAlign = TextAlign.Center)
    }
}

/** An icon-only action whose label also shows as a tooltip on long press or hover. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TooltipIconButton(icon: ImageVector, label: String, onClick: () -> Unit, tint: Color = AppColors.textPrimary) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        IconButton(onClick = onClick) { Icon(icon, contentDescription = label, tint = tint) }
    }
}

/**
 * Title row of a bottom sheet: one title size everywhere, with an optional close button.
 * Sheets keep the default drag handle above it.
 */
@Composable
fun SheetHeader(title: String, modifier: Modifier = Modifier, onClose: (() -> Unit)? = null) {
    Row(
        modifier = modifier
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = AppColors.textPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp),
        )
        if (onClose != null) {
            TooltipIconButton(Icons.Outlined.Close, stringResource(R.string.action_close), onClose)
        }
    }
}
