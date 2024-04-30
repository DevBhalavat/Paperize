package com.anthonyla.paperize.feature.wallpaper.presentation.album_view_screen


import android.app.Application
import android.content.Context
import android.webkit.MimeTypeMap
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.anthonyla.paperize.feature.wallpaper.domain.model.Folder
import com.anthonyla.paperize.feature.wallpaper.domain.model.Wallpaper
import com.anthonyla.paperize.feature.wallpaper.domain.repository.AlbumRepository
import com.lazygeniouz.dfc.file.DocumentFileCompat
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AlbumViewScreenViewModel @Inject constructor(
    application: Application,
    private val repository: AlbumRepository,
) : AndroidViewModel(application) {
    private val context: Context
        get() = getApplication<Application>().applicationContext
    private val _state = MutableStateFlow(AlbumViewState())
    val state = _state.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000), AlbumViewState()
    )

    fun onEvent(event: AlbumViewEvent) {
        when (event) {
            is AlbumViewEvent.DeleteSelected -> {
                viewModelScope.launch {
                    event.albumsWithWallpaper.let { album ->
                        val folders = album.folders.filter { _state.value.selectedFolders.contains(it.folderUri)}
                        val wallpapers = album.wallpapers.filter { _state.value.selectedWallpapers.contains(it.wallpaperUri)}
                        val doesContainCover = wallpapers.any { it.wallpaperUri == album.album.coverUri }
                        repository.deleteFolderList(folders)
                        repository.deleteWallpaperList(wallpapers)
                        if (doesContainCover) {
                            repository.updateAlbum(album.album.copy(coverUri = null))
                        }
                    }
                }
            }
            is AlbumViewEvent.SelectAll -> {
                viewModelScope.launch {
                    event.albumsWithWallpaper.let { album ->
                        if (!_state.value.allSelected) {
                            _state.update { state ->
                                state.copy(
                                    selectedFolders = album.folders.map { it.folderUri },
                                    selectedWallpapers = album.wallpapers.map { it.wallpaperUri },
                                    selectedCount = album.folders.size + album.wallpapers.size,
                                    maxSize = album.folders.size + album.wallpapers.size
                                )
                            }
                            updateAllSelected()
                        }
                    }
                }
            }

            is AlbumViewEvent.DeselectAll -> {
                viewModelScope.launch {
                    _state.update {
                        it.copy(
                            selectedFolders = emptyList(),
                            selectedWallpapers = emptyList(),
                            selectedCount = 0,
                            allSelected = false
                        )
                    }
                }
            }

            is AlbumViewEvent.SelectFolder -> {
                viewModelScope.launch {
                    if (!_state.value.selectedFolders.any { it == event.directoryUri }) {
                        _state.update {
                            it.copy(
                                selectedFolders = it.selectedFolders.plus(event.directoryUri),
                                selectedCount = it.selectedCount + 1
                            )
                        }
                        updateAllSelected()
                    }
                }
            }

            is AlbumViewEvent.SelectWallpaper -> {
                viewModelScope.launch {
                    if (!_state.value.selectedWallpapers.any { it == event.wallpaperUri }) {
                        _state.update {
                            it.copy(
                                selectedWallpapers = it.selectedWallpapers.plus(event.wallpaperUri),
                                selectedCount = it.selectedCount + 1
                            )
                        }
                        updateAllSelected()
                    }
                }
            }

            is AlbumViewEvent.RemoveFolderFromSelection -> {
                viewModelScope.launch {
                    if (_state.value.selectedFolders.find { it == event.directoryUri } != null) {
                        _state.update {
                            it.copy(
                                selectedFolders = it.selectedFolders.minus(event.directoryUri),
                                selectedCount = it.selectedCount - 1
                            )
                        }
                        updateAllSelected()
                    }
                }
            }

            is AlbumViewEvent.RemoveWallpaperFromSelection -> {
                viewModelScope.launch {
                    if (_state.value.selectedWallpapers.find { it == event.wallpaperUri } != null) {
                        _state.update {
                            it.copy(
                                selectedWallpapers = it.selectedWallpapers.minus(event.wallpaperUri),
                                selectedCount = it.selectedCount - 1
                            )
                        }
                        updateAllSelected()
                    }
                }
            }

            is AlbumViewEvent.ClearState -> {
                viewModelScope.launch {
                    _state.update {
                        it.copy(
                            selectedFolders = emptyList(),
                            selectedWallpapers = emptyList(),
                            allSelected = false,
                            selectedCount = 0,
                            maxSize = 0
                        )
                    }
                }
            }

            is AlbumViewEvent.SetSize -> {
                viewModelScope.launch {
                    _state.update {
                        it.copy(
                            maxSize = event.size
                        )
                    }
                }
            }

            is AlbumViewEvent.AddWallpapers -> {
                viewModelScope.launch {
                    val newWallpaperUris = event.wallpaperUris.filterNot { it in event.album.wallpapers.map { wallpaper -> wallpaper.wallpaperUri } }
                    val newWallpapers = newWallpaperUris.map { uri ->
                        Wallpaper(
                            initialAlbumName = event.album.album.initialAlbumName,
                            wallpaperUri = uri,
                            key = uri.hashCode() + event.album.album.initialAlbumName.hashCode(),
                        )
                    }
                    repository.upsertWallpaperList(newWallpapers)
                    _state.update {
                        it.copy(
                            selectedFolders = emptyList(),
                            selectedWallpapers = emptyList(),
                            allSelected = false,
                            selectedCount = 0,
                            maxSize = _state.value.maxSize + newWallpapers.size
                        )
                    }
                }
            }

            is AlbumViewEvent.AddFolder -> {
                viewModelScope.launch {
                    if (event.directoryUri !in event.album.folders.map { it.folderUri }) {
                        val wallpapers: List<String> = getWallpaperFromFolder(event.directoryUri, context)
                        val folderName = getFolderNameFromUri(event.directoryUri, context)
                        repository.upsertFolder(
                            Folder(
                                folderUri = event.directoryUri,
                                folderName = folderName,
                                initialAlbumName = event.album.album.initialAlbumName,
                                key = event.directoryUri.hashCode() + event.album.album.initialAlbumName.hashCode(),
                                coverUri = wallpapers.randomOrNull(),
                                wallpapers = wallpapers
                            )
                        )
                        _state.update {
                            it.copy(
                                selectedFolders = emptyList(),
                                selectedWallpapers = emptyList(),
                                allSelected = false,
                                selectedCount = 0,
                                maxSize = _state.value.maxSize + 1
                            )
                        }
                    }
                }
            }
        }
    }

    private fun updateAllSelected() {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    allSelected = it.selectedFolders.size + it.selectedWallpapers.size >= _state.value.maxSize
                )
            }
        }
    }

    private fun getWallpaperFromFolder(folderUri: String, context: Context): List<String> {
        val folderDocumentFile = DocumentFileCompat.fromTreeUri(context, folderUri.toUri())
        return listFilesRecursive(folderDocumentFile, context)
    }

    private fun listFilesRecursive(parent: DocumentFileCompat?, context: Context): List<String> {
        val files = mutableListOf<String>()
        parent?.listFiles()?.forEach { file ->
            if (file.isDirectory()) {
                files.addAll(listFilesRecursive(file, context))
            } else {
                val extension = MimeTypeMap.getFileExtensionFromUrl(file.uri.toString())
                val allowedExtensions = listOf("jpg", "png", "heif", "webp")
                if (extension in allowedExtensions) {
                    files.add(file.uri.toString())
                }
            }
        }
        return files
    }

    private fun getFolderNameFromUri(folderUri: String, context: Context): String? {
        return DocumentFileCompat.fromTreeUri(context, folderUri.toUri())?.name
    }
}