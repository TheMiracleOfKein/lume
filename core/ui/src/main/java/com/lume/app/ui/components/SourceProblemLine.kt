package com.lume.app.ui.components

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lume.app.R
import com.lume.app.data.source.SourceProblem
import com.lume.app.ui.theme.AppColors

/** "Last error 5 minutes ago ...", the line a source with a recent failed call shows. */
@Composable
fun sourceProblemText(problem: SourceProblem): String {
    val ago = DateUtils.getRelativeTimeSpanString(problem.at, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
    return if (problem.siteCheck) stringResource(R.string.source_problem_site_check, ago)
    else stringResource(R.string.source_problem, ago, problem.message)
}

/** The problem line with a warning icon, in the danger colour. */
@Composable
fun SourceProblemLine(problem: SourceProblem, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Outlined.Warning,
            contentDescription = null,
            tint = AppColors.danger,
            modifier = Modifier.size(14.dp),
        )
        Text(
            sourceProblemText(problem),
            color = AppColors.danger,
            fontSize = 11.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}
