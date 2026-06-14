package forpdateam.ru.forpda.presentation.devdb.device

import forpdateam.ru.forpda.presentation.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import forpdateam.ru.forpda.entity.remote.devdb.Device
import forpdateam.ru.forpda.presentation.ILinkHandler
import forpdateam.ru.forpda.presentation.TabRouter
import forpdateam.ru.forpda.ui.fragments.devdb.device.posts.PostsFragment

@HiltViewModel
class SubDeviceViewModel @Inject constructor(
        private val router: TabRouter,
        private val linkHandler: ILinkHandler
) : BaseViewModel() {

    fun onCommentClick(item: Device.Comment) {
        linkHandler.handle("https://4pda.to/forum/index.php?showuser=${item.userId}", router)
    }

    fun onPostClick(item: Device.PostItem, source: Int) {
        val url = if (source == PostsFragment.SRC_NEWS) {
            "https://4pda.to/index.php?p=${item.id}"
        } else {
            "https://4pda.to/forum/index.php?showtopic=${item.id}"
        }
        linkHandler.handle(url, router)
    }

}
