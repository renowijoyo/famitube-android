package com.famitube.app.ui.screens.playlists

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.famitube.app.data.model.Playlist
import com.famitube.app.data.repository.FamiTubeRepository
import com.famitube.app.ui.screens.library.LibraryScreen

@Composable
fun PlaylistsScreen(
    repository: FamiTubeRepository,
    onPlaylistClick: ((Playlist) -> Unit)? = null,
    onVideoClick: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    LibraryScreen(
        repository = repository,
        onVideoClick = { videoId ->
            onVideoClick?.invoke(videoId)
        },
        modifier = modifier
    )
}
