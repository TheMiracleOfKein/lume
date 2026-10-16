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
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
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
            .clickable(enabled = readOnly, onClick = onSubmit)
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
                    modifier = Modifier.fillMaxWidth(),
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
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
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
                        color = Color.Black,
                        fontSize = 10.sp,
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
) {
    val highlight by animateColorAsState(
        if (selected) AppColors.accent.copy(alpha = 0.2f) else Color.Transparent,
        Motion.short(),
        label = "logo",
    )
    Image(
        painter = painterResource(R.drawable.ic_lume),
        contentDescription = stringResource(R.string.app_name),
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.22f))
            .background(highlight),
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
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(
                containerColor = AppColors.accent,
                contentColor = Color.Black,
            ),
        ) {
            Text(stringResource(R.string.action_retry))
        }
        if (onCloudflare != null) {
            TextButton(onClick = onCloudflare) {
                Text("Cloudflare", color = AppColors.accent)
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
            .then(if (onTap != null) Modifier.clickable(onClick = onTap) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = AppColors.textPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
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
) {
    Column(
        modifier = modifier
            .width(width)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
    ) {
        Box {
            CoverImage(url = coverUrl, width = width, height = width * 1.4f, radius = 10.dp)
            if (!badge.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(badge, color = Color.White, fontSize = 10.sp, maxLines = 1)
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

/** Small empty-state message, centered. */
@Composable
fun EmptyBox(message: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(message, color = AppColors.textSecondary, fontSize = 15.sp)
    }
}
