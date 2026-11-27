package com.lume.app.ui.screens
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lume.app.R
import com.lume.app.ui.theme.AppColors

/** "Also on MangaDex, Comick": sources Lume found the title on, with Link / Not now. */
@Composable
internal fun AlsoOnCard(
    names: List<String>,
    enabled: Boolean,
    onOpen: () -> Unit,
    onLink: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .padding(top = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.card)
            .clickable(onClick = onOpen)
            .padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 4.dp),
    ) {
        Text(
            stringResource(R.string.title_also_on, names.joinToString()),
            color = AppColors.textPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
        )
        Text(
            stringResource(R.string.title_also_on_hint),
            color = AppColors.textSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 2.dp, end = 8.dp),
        )
        Row(modifier = Modifier.align(androidx.compose.ui.Alignment.End)) {
            TextButton(onClick = onDismiss, enabled = enabled) { Text(stringResource(R.string.action_not_now)) }
            TextButton(onClick = onLink, enabled = enabled) { Text(stringResource(R.string.action_link)) }
        }
    }
}
