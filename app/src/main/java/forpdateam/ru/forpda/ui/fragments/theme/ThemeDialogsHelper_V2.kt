package forpdateam.ru.forpda.ui.fragments.theme

import android.content.Context
import android.view.LayoutInflater
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.entity.common.AuthData
import forpdateam.ru.forpda.entity.remote.IBaseForumPost
import forpdateam.ru.forpda.model.AuthHolder
import forpdateam.ru.forpda.model.preferences.OtherPreferencesHolder
import forpdateam.ru.forpda.model.preferences.TopicPreferencesHolder
import forpdateam.ru.forpda.presentation.theme.ThemeWebCallbacks
import forpdateam.ru.forpda.databinding.ReportLayoutBinding
import forpdateam.ru.forpda.databinding.ReputationChangeLayoutBinding
import forpdateam.ru.forpda.ui.views.DynamicDialogMenu
import forpdateam.ru.forpda.ui.views.dialog.showWithStyledButtons
import forpdateam.ru.forpda.common.Preferences as AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Created by radiationx on 01.11.16.
 *
 * [scope] is supplied by the host so the helper does NOT own a process-wide
 * `MainScope()` that would outlive the view. Callers pass the fragment's
 * `viewLifecycleOwner.lifecycleScope` (constructed from `onViewCreated()`), so
 * the report-warning preference write in `tryReportPost` is cancelled with the
 * view.
 */
class ThemeDialogsHelper_V2(
    private val context: Context,
    private val authHolder: AuthHolder,
    private val otherPreferencesHolder: OtherPreferencesHolder,
    private val topicPreferencesHolder: TopicPreferencesHolder,
    private val scope: CoroutineScope,
    private val enableForumBlacklistMenu: Boolean = true
) {
    private companion object {
        const val POST_ACTION_REPLY = 0
        const val POST_ACTION_QUOTE_FROM_CLIPBOARD = 1
        const val POST_ACTION_COPY_LINK = 2
        const val POST_ACTION_SHARE = 3
        const val POST_ACTION_REPORT = 4
        const val POST_ACTION_EDIT = 5
        const val POST_ACTION_DELETE = 6
        const val POST_ACTION_CREATE_NOTE = 7
        const val USER_ACTION_FORUM_BLACKLIST = 6
    }

    private val userMenu = DynamicDialogMenu<ThemeWebCallbacks, IBaseForumPost>()
    private val reputationMenu = DynamicDialogMenu<ThemeWebCallbacks, IBaseForumPost>()
    private val postMenu = DynamicDialogMenu<ThemeWebCallbacks, IBaseForumPost>()

    init {
        userMenu.addItem(context.getString(R.string.profile)) { ctx, data -> ctx.openProfile(data.id) }
        userMenu.addItem(context.getString(R.string.reputation)) { ctx, data -> ctx.onReputationMenuClick(data.id) }
        userMenu.addItem(context.getString(R.string.pm_qms)) { ctx, data -> ctx.openQms(data.id) }
        userMenu.addItem(context.getString(R.string.user_themes)) { ctx, data -> ctx.openSearchUserTopic(data.id) }
        userMenu.addItem(context.getString(R.string.messages_in_this_theme)) { ctx, data -> ctx.openSearchInTopic(data.id) }
        userMenu.addItem(context.getString(R.string.user_messages)) { ctx, data -> ctx.openSearchUserMessages(data.id) }
        userMenu.addItem(context.getString(R.string.forum_blacklist_add)) { ctx, data -> ctx.toggleForumBlacklist(data.id) }

        reputationMenu.addItem(context.getString(R.string.increase)) { ctx, data -> ctx.onChangeReputationClick(data.id, true) }
        reputationMenu.addItem(context.getString(R.string.look)) { ctx, data -> ctx.openReputationHistory(data.id) }
        reputationMenu.addItem(context.getString(R.string.decrease)) { ctx, data -> ctx.onChangeReputationClick(data.id, false) }

        postMenu.addItem(context.getString(R.string.reply)) { ctx, data -> ctx.onReplyPostClick(data.id) }
        postMenu.addItem(context.getString(R.string.quote_from_clipboard)) { ctx, data -> ctx.quoteFromBuffer(data.id) }
        postMenu.addItem(context.getString(R.string.copy_post_link)) { ctx, data -> ctx.copyPostLink(data.id) }
        postMenu.addItem(context.getString(R.string.share_post_link)) { ctx, data -> ctx.sharePostLink(data.id) }
        postMenu.addItem(context.getString(R.string.report)) { ctx, data -> ctx.onReportPostClick(data.id) }
        postMenu.addItem(context.getString(R.string.edit)) { ctx, data -> ctx.onEditPostClick(data.id) }
        postMenu.addItem(context.getString(R.string.delete)) { ctx, data -> ctx.onDeletePostClick(data.id) }
        postMenu.addItem(context.getString(R.string.create_note)) { ctx, data -> ctx.createNote(data.id) }
    }

    fun showUserMenu(presenter: ThemeWebCallbacks, post: IBaseForumPost) {
        userMenu.disallowAll()
        userMenu.allow(0)
        userMenu.allow(1)
        val authData: AuthData = authHolder.get()
        if (authData.isAuth() && post.userId != authData.userId) {
            userMenu.allow(2)
        }
        userMenu.allow(3)
        userMenu.allow(4)
        userMenu.allow(5)
        if (enableForumBlacklistMenu && (post.userId <= 0 || post.userId != authData.userId)) {
            val blacklistLabel = context.getString(
                    if (topicPreferencesHolder.isForumBlacklisted(post.userId, post.nick)) {
                        R.string.forum_blacklist_remove
                    } else {
                        R.string.forum_blacklist_add
                    }
            )
            userMenu.changeTitle(USER_ACTION_FORUM_BLACKLIST, blacklistLabel)
            userMenu.allow(USER_ACTION_FORUM_BLACKLIST)
        }
        userMenu.show(context, presenter, post)
    }

    fun showReputationMenu(presenter: ThemeWebCallbacks, post: IBaseForumPost) {
        reputationMenu.disallowAll()
        if (!authHolder.get().isAuth() || post.canPlusRep) {
            reputationMenu.allow(0)
        }
        reputationMenu.allow(1)
        if (!authHolder.get().isAuth() || post.canMinusRep) {
            reputationMenu.allow(2)
        }
        val title = context.getString(R.string.reputation) + " ".plus(post.nick)
        reputationMenu.show(context, presenter, post, title)
    }

    fun showPostMenu(presenter: ThemeWebCallbacks, post: IBaseForumPost, density: AppPreferences.Main.TopicPostDensity? = null) {
        postMenu.disallowAll()
        if (!authHolder.get().isAuth() || post.canQuote) {
            postMenu.allow(POST_ACTION_REPLY)
            postMenu.allow(POST_ACTION_QUOTE_FROM_CLIPBOARD)
        }
        postMenu.allow(POST_ACTION_COPY_LINK)
        postMenu.allow(POST_ACTION_SHARE)
        val authData: AuthData = authHolder.get()
        if (authData.isAuth()) {
            if (post.canReport) postMenu.allow(POST_ACTION_REPORT)
            if (post.canEdit) postMenu.allow(POST_ACTION_EDIT)
            if (post.canDelete) postMenu.allow(POST_ACTION_DELETE)
        }
        postMenu.allow(POST_ACTION_CREATE_NOTE)
        postMenu.show(
            context,
            presenter,
            post,
            context.getString(R.string.post_actions_title),
            density?.let { PostActionMenuDensityStyle.from(it) }
        )
    }

    private object PostActionMenuDensityStyle {
        fun from(density: AppPreferences.Main.TopicPostDensity): DynamicDialogMenu.Style =
            when (density) {
                AppPreferences.Main.TopicPostDensity.SUPER_COMPACT -> DynamicDialogMenu.Style(
                    titleTextSizeSp = 18f,
                    itemTextSizeSp = 14f,
                    itemMinHeightDp = 40,
                    contentVerticalPaddingDp = 6,
                    itemVerticalPaddingDp = 6,
                    titleBottomPaddingDp = 4
                )
                AppPreferences.Main.TopicPostDensity.COMPACT -> DynamicDialogMenu.Style(
                    titleTextSizeSp = 19f,
                    itemTextSizeSp = 15f,
                    itemMinHeightDp = 46,
                    contentVerticalPaddingDp = 8,
                    itemVerticalPaddingDp = 7,
                    titleBottomPaddingDp = 6
                )
                AppPreferences.Main.TopicPostDensity.COMFORTABLE -> DynamicDialogMenu.Style(
                    titleTextSizeSp = 20f,
                    itemTextSizeSp = 16f,
                    itemMinHeightDp = 48,
                    contentVerticalPaddingDp = 10,
                    itemVerticalPaddingDp = 8,
                    titleBottomPaddingDp = 8
                )
            }
    }

    fun tryReportPost(presenter: ThemeWebCallbacks, post: IBaseForumPost) {
        if (otherPreferencesHolder.getShowReportWarningSync()) {
            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.attention)
                .setMessage(R.string.report_warning)
                .setPositiveButton(R.string.ok) { _, _ ->
                    scope.launch { otherPreferencesHolder.setShowReportWarning(false) }
                    showReportDialog(presenter, post)
                }
                .setNegativeButton(R.string.cancel, null)
                .showWithStyledButtons()
        } else {
            showReportDialog(presenter, post)
        }
    }

    fun showReportDialog(presenter: ThemeWebCallbacks, post: IBaseForumPost) {
        val builder = MaterialAlertDialogBuilder(context)
        val binding = ReportLayoutBinding.inflate(LayoutInflater.from(builder.context))

        builder
            .setTitle(String.format(context.getString(R.string.report_to_post_Nick), post.nick))
            .setView(binding.root)
            .setPositiveButton(R.string.send) { _, _ -> presenter.reportPost(post.id, binding.reportTextField.text.toString()) }
            .setNegativeButton(R.string.cancel, null)
            .showWithStyledButtons()
    }

    fun deletePost(presenter: ThemeWebCallbacks, post: IBaseForumPost) {
        MaterialAlertDialogBuilder(context)
            .setMessage(String.format(context.getString(R.string.ask_delete_post_Nick), post.nick))
            .setPositiveButton(R.string.ok) { _, _ -> presenter.deletePost(post.id) }
            .setNegativeButton(R.string.cancel, null)
            .showWithStyledButtons()
    }

    fun changeReputation(presenter: ThemeWebCallbacks, post: IBaseForumPost, type: Boolean) {
        val builder = MaterialAlertDialogBuilder(context)
        val binding = ReputationChangeLayoutBinding.inflate(LayoutInflater.from(builder.context))
        binding.reputationText.text = String.format(context.getString(R.string.change_reputation_Type_Nick), context.getString(if (type) R.string.increase else R.string.decrease), post.nick)

        builder
            .setView(binding.root)
            .setPositiveButton(R.string.ok) { _, _ -> presenter.changeReputation(post.id, type, binding.reputationTextField.text.toString()) }
            .setNegativeButton(R.string.cancel, null)
            .showWithStyledButtons()
    }

    fun votePost(presenter: ThemeWebCallbacks, post: IBaseForumPost, type: Boolean) {
        MaterialAlertDialogBuilder(context)
            .setMessage(String.format(context.getString(R.string.change_post_reputation_Type_Nick), context.getString(if (type) R.string.increase else R.string.decrease), post.nick))
            .setPositiveButton(R.string.ok) { _, _ -> presenter.votePost(post.id, type) }
            .setNegativeButton(R.string.cancel, null)
            .showWithStyledButtons()
    }

    fun openAnchorDialog(presenter: ThemeWebCallbacks, post: IBaseForumPost, anchorName: String) {
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.link_to_anchor)
            .setPositiveButton(R.string.copy) { _, _ -> presenter.copyAnchorLink(post.id, anchorName) }
            .setNegativeButton(R.string.cancel, null)
            .showWithStyledButtons()
    }

    fun openSpoilerLinkDialog(presenter: ThemeWebCallbacks, post: IBaseForumPost, spoilNumber: String) {
        MaterialAlertDialogBuilder(context)
            .setMessage(R.string.spoiler_link_copy_ask)
            .setPositiveButton(R.string.ok) { _, _ -> presenter.copySpoilerLink(post.id, spoilNumber) }
            .setNegativeButton(R.string.cancel, null)
            .showWithStyledButtons()
    }
}
