package forpdateam.ru.forpda.presentation.devdb.device

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import forpdateam.ru.forpda.common.topicUrlWithUnreadIfPlainOpen
import forpdateam.ru.forpda.entity.remote.devdb.Device
import forpdateam.ru.forpda.presentation.ILinkHandler
import forpdateam.ru.forpda.presentation.TabRouter
import forpdateam.ru.forpda.ui.fragments.devdb.device.posts.PostsFragment

class SubDeviceViewModel(
        private val router: TabRouter,
        private val linkHandler: ILinkHandler
) : ViewModel() {

    fun onCommentClick(item: Device.Comment) {
        linkHandler.handle("https://4pda.to/forum/index.php?showuser=${item.userId}", router)
    }

    fun onPostClick(item: Device.PostItem, source: Int) {
        val url = if (source == PostsFragment.SRC_NEWS) {
            "https://4pda.to/index.php?p=${item.id}"
        } else {
            topicUrlWithUnreadIfPlainOpen(
                    Uri.parse("https://4pda.to/forum/index.php?showtopic=${item.id}")
            )
        }
        linkHandler.handle(url, router)
    }

    class Factory(
            private val router: TabRouter,
            private val linkHandler: ILinkHandler
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass != SubDeviceViewModel::class.java) throw IllegalArgumentException("Unknown ViewModel class")
            return SubDeviceViewModel(router, linkHandler) as T
        }
    }
}
