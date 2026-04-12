package forpdateam.ru.forpda.presentation.profile

import android.graphics.Bitmap
import forpdateam.ru.forpda.common.ui.IBaseView
import forpdateam.ru.forpda.entity.remote.profile.ProfileModel

/**
 * Created by radiationx on 02.01.18.
 */
interface ProfileView : IBaseView {
    fun showProfile(data: ProfileModel)

    fun showAvatar(bitmap: Bitmap)

    fun onSaveNote(success: Boolean)
}
