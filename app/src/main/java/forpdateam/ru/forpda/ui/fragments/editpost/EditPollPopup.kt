package forpdateam.ru.forpda.ui.fragments.editpost

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.simple.SimpleTextWatcher
import forpdateam.ru.forpda.databinding.EditPollBinding
import forpdateam.ru.forpda.entity.remote.editpost.EditPoll

/**
 * Created by radiationx on 28.07.17.
 */
class EditPollPopup(private val context: Context) {
    private val dialog: BottomSheetDialog = BottomSheetDialog(context)
    private val binding: EditPollBinding = EditPollBinding.inflate(LayoutInflater.from(context), null, false)

    private val pollTitle: TextView = binding.pollTitle
    private val pollTitleField: EditText = binding.pollTitleField
    private val addPoll: ImageButton = binding.addPoll
    private val questionsView: RecyclerView = binding.pollQuestions

    private var questionsAdapter: PollQuestionsAdapter? = null
    private var poll: EditPoll? = null

    init {
        dialog.setOnShowListener {
            dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        questionsView.layoutManager = LinearLayoutManager(questionsView.context)
        addPoll.setOnClickListener { questionsAdapter?.add(EditPoll.Question()) }
        pollTitleField.addTextChangedListener(object : SimpleTextWatcher() {
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                poll?.title = s.toString()
            }
        })
    }

    fun show() {
        if (binding.root.parent is ViewGroup) {
            (binding.root.parent as ViewGroup).removeView(binding.root)
        }
        dialog.setContentView(binding.root)
        dialog.window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
        )
        dialog.show()
    }

    fun setPoll(poll: EditPoll) {
        this.poll = poll
        pollTitleField.setText(poll.title)
        questionsAdapter = PollQuestionsAdapter(context, poll.questions, poll)
        questionsView.adapter = questionsAdapter
    }
}
