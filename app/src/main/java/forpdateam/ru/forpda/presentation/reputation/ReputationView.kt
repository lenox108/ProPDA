package forpdateam.ru.forpda.presentation.reputation

import forpdateam.ru.forpda.common.ui.IBaseView
import forpdateam.ru.forpda.entity.remote.reputation.RepData
import forpdateam.ru.forpda.entity.remote.reputation.RepItem

/**
 * Created by radiationx on 03.01.18.
 */
interface ReputationView : IBaseView {
    fun showReputation(repData: RepData)

    fun onChangeReputation(result: Boolean)

    fun showItemDialogMenu(item: RepItem)

    fun showAvatar(avatarUrl: String)
}
