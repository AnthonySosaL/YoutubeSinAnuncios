package com.vancedclean.app.viewmodel

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.comments.CommentsInfo
import org.schabi.newpipe.extractor.comments.CommentsInfoItem

data class CommentItem(
    val author: String,
    val avatarUrl: String?,
    val text: String,
    val likeCount: Int,
    val date: String,
    val replyCount: Int,
    val pinned: Boolean,
)

data class CommentsState(
    val comments: List<CommentItem> = emptyList(),
    val count: Int = -1,
    val isLoading: Boolean = false,
    val hasMore: Boolean = false,
    val disabled: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null,
)

/** Carga paginada de comentarios con NewPipe (como CommentYtbDataService de la app vieja). */
class CommentsLoader(private val scope: CoroutineScope) {

    private val _state = MutableStateFlow(CommentsState())
    val state: StateFlow<CommentsState> = _state.asStateFlow()

    private var url = ""
    private var info: CommentsInfo? = null
    private var nextPage: Page? = null
    private var job: Job? = null

    fun reset(videoUrl: String) {
        job?.cancel()
        url = videoUrl
        info = null
        nextPage = null
        _state.value = CommentsState()
    }

    /** Primera página o la siguiente, según lo ya cargado. */
    fun loadMore() {
        val current = _state.value
        if (current.isLoading || current.disabled) return
        if (current.loaded && nextPage == null) return
        _state.value = current.copy(isLoading = true, error = null)
        val requestedUrl = url
        job = scope.launch(Dispatchers.IO) {
            try {
                val service = NewPipe.getService(0)
                val loadedInfo = info
                val items: List<CommentsInfoItem>
                val next: Page?
                var count = _state.value.count
                if (loadedInfo == null) {
                    val first = CommentsInfo.getInfo(service, requestedUrl)
                    if (first.isCommentsDisabled) {
                        _state.value = CommentsState(disabled = true, loaded = true)
                        return@launch
                    }
                    info = first
                    count = first.commentsCount
                    items = first.relatedItems.filterIsInstance<CommentsInfoItem>()
                    next = first.nextPage
                } else {
                    val more = CommentsInfo.getMoreItems(service, loadedInfo, nextPage)
                    items = more.items.filterIsInstance<CommentsInfoItem>()
                    next = more.nextPage
                }
                if (requestedUrl != url) return@launch
                nextPage = next
                _state.value = _state.value.copy(
                    comments = _state.value.comments + items.map { it.toComment() },
                    count = count,
                    isLoading = false,
                    loaded = true,
                    hasMore = next != null,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message ?: "Error cargando comentarios")
            }
        }
    }

    private fun CommentsInfoItem.toComment() = CommentItem(
        author = uploaderName.orEmpty(),
        avatarUrl = uploaderAvatars.firstOrNull()?.url,
        text = commentText?.content.orEmpty()
            .replace(Regex("""<br\s*/?>"""), "\n")
            .replace(Regex("""<[^>]+>"""), "")
            .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
            .replace("&lt;", "<").replace("&gt;", ">"),
        likeCount = likeCount,
        date = textualUploadDate.orEmpty(),
        replyCount = replyCount,
        pinned = isPinned,
    )
}
