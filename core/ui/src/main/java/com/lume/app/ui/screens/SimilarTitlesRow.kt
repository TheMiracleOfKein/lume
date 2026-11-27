package com.lume.app.ui.screens
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lume.app.R
import com.lume.app.data.similar.SimilarResult
import com.lume.app.data.similar.SimilarTitle
import com.lume.app.ui.components.CoverImage
import com.lume.app.ui.theme.AppColors

/**
 * "Similar titles": covers in a row; a tap looks the title up on this title's source. The cover
 * named [lookingUp] shows a spinner while that search runs.
 */
@Composable
internal fun SimilarTitlesRow(result: SimilarResult, lookingUp: String?, onOpen: (SimilarTitle) -> Unit) {
    Column(modifier = Modifier.padding(top = 16.dp)) {
        Text(
            stringResource(R.string.title_similar),
            color = AppColors.textPrimary,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            stringResource(R.string.title_similar_from, result.from),
            color = AppColors.textTertiary,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 2.dp),
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(result.items, key = { it.title }) { item ->
                Column(
                    modifier = Modifier
                        .width(96.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClickLabel = stringResource(R.string.cd_similar_search)) { onOpen(item) },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CoverImage(url = item.coverUrl, width = 96.dp, height = 136.dp, radius = 8.dp)
                        if (lookingUp == item.title) {
                            Box(
                                Modifier
                                    .matchParentSize()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black.copy(alpha = 0.45f)),
                            )
                            CircularProgressIndicator(color = AppColors.accent, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
                        }
                    }
                    Text(
                        item.title,
                        color = AppColors.textSecondary,
                        fontSize = 12.sp,
                        lineHeight = 15.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}
