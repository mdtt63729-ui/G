package com.gitofy.core.designsystem.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * GITOFY's pull-to-refresh wrapper built on top of the Material 3
 * [PullToRefreshBox].
 *
 * PRD §17: Pull-to-Refresh.
 *
 * @param isRefreshing Whether a refresh is currently in progress.
 * @param onRefresh    Invoked when the user completes a pull-to-refresh gesture.
 * @param modifier     Modifier applied to the container.
 * @param state        Optional [PullToRefreshState] to control/observe the gesture.
 * @param content      The scrollable content displayed inside the refresh box.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GITOFYPullToRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    state: PullToRefreshState = rememberPullToRefreshState(),
    content: @Composable () -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        state = state,
        modifier = modifier,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = state,
                isRefreshing = isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
    ) {
        content()
    }
}
