package com.lume.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lume.app.R
import com.lume.app.data.novel.NovelBlock
import com.lume.app.data.prefs.NovelReaderSettings

/**
 * The text of the loaded chapters as a scrolling list. Sizes are in sp, so the system font scale
 * applies on top of the reader's own text size setting.
 */
@Composable
internal fun NovelTextList(
    items: List<NovelItem>,
    settings: NovelReaderSettings,
    palette: NovelPalette,
    listState: LazyListState,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val body = TextStyle(
        color = palette.text,
        fontFamily = settings.font.family(),
        fontSize = settings.fontSize.sp,
        lineHeight = (settings.fontSize * settings.lineHeight).sp,
        textAlign = if (settings.justify) TextAlign.Justify else TextAlign.Start,
        // Justified lines break with hyphens, so words are not spread into huge gaps.
        hyphens = if (settings.justify) Hyphens.Auto else Hyphens.None,
        lineBreak = if (settings.justify) LineBreak.Paragraph else LineBreak.Simple,
    )
    LazyColumn(
        state = listState,
        contentPadding = contentPadding,
        modifier = modifier.fillMaxSize().background(palette.background),
    ) {
        items(items, key = { it.key }) { item ->
            when (item) {
                is NovelItem.Header -> Text(
                    text = item.chapter.title,
                    style = body.copy(
                        fontSize = (settings.fontSize * 1.3f).sp,
                        lineHeight = (settings.fontSize * 1.3f * 1.3f).sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Start,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = settings.sideMargin.dp, end = settings.sideMargin.dp, top = 28.dp, bottom = 20.dp)
                        .semantics { heading() },
                )
                is NovelItem.Block -> NovelBlockView(item.block, settings, palette, body)
            }
        }
    }
}

@Composable
private fun NovelBlockView(block: NovelBlock, settings: NovelReaderSettings, palette: NovelPalette, body: TextStyle) {
    val side = settings.sideMargin.dp
    val after = settings.paragraphSpacing.dp
    when (block) {
        is NovelBlock.Paragraph -> Text(
            text = block.spans.toAnnotatedString(),
            style = body,
            modifier = Modifier.fillMaxWidth().padding(start = side, end = side, bottom = after),
        )
        is NovelBlock.Heading -> {
            val scale = headingScale(block.level)
            Text(
                text = block.spans.toAnnotatedString(),
                style = body.copy(
                    fontSize = (settings.fontSize * scale).sp,
                    lineHeight = (settings.fontSize * scale * settings.lineHeight).sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Start,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = side, end = side, top = 12.dp, bottom = after)
                    .semantics { heading() },
            )
        }
        is NovelBlock.Image -> AsyncImage(
            model = block.src,
            contentDescription = stringResource(R.string.novel_illustration),
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth().padding(bottom = after),
        )
        NovelBlock.Divider -> {
            val label = stringResource(R.string.novel_scene_break)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp + after / 2)
                    .semantics { contentDescription = label },
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "*   *   *", style = body.copy(color = palette.textSecondary, textAlign = TextAlign.Center))
            }
        }
    }
}
