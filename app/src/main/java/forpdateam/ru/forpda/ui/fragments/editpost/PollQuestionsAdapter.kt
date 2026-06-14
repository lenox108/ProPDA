package forpdateam.ru.forpda.ui.fragments.editpost

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.recyclerview.widget.RecyclerView
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.simple.SimpleTextWatcher
import forpdateam.ru.forpda.databinding.EditPollQuestionBinding
import forpdateam.ru.forpda.entity.remote.editpost.EditPoll
import forpdateam.ru.forpda.ui.views.dialog.showWithStyledButtons

class PollQuestionsAdapter(
    private val context: Context,
    private val questions: MutableList<EditPoll.Question>,
    private val poll: EditPoll
) : RecyclerView.Adapter<PollQuestionsAdapter.ViewHolder>() {

    private val choiceAdapters = HashMap<EditPoll.Question, PollChoicesAdapter>()

    constructor(context: Context) : this(context, mutableListOf(), EditPoll())

    fun add(question: EditPoll.Question) {
        if (questions.size < poll.maxQuestions) {
            poll.increaseIndexOffset()
            question.index = poll.indexOffset + poll.baseIndexOffset
            questions.add(question)
            notifyItemInserted(questions.size - 1)
        } else {
            Toast.makeText(context, context.getString(R.string.poll_questions_Max, poll.maxQuestions), Toast.LENGTH_SHORT).show()
        }
    }

    fun getItem(position: Int): EditPoll.Question = questions[position]

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = EditPollQuestionBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding, CustomTextWatcher(), CustomCheckedChangeListener())
    }

    override fun getItemCount(): Int = questions.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(holder.adapterPosition)

        val qstr = String.format(
            context.getString(R.string.poll_question_Pos),
            holder.adapterPosition + 1
        )
        holder.customTextWatcher.updatePosition(holder.adapterPosition)
        holder.checkedChangeListener.updatePosition(holder.adapterPosition)

        holder.binding.pollQuestionTitle.text = qstr
        holder.binding.pollQuestionTitleField.setText(item.title)
        holder.binding.pollQuestionTitleField.hint = qstr

        holder.binding.pollQuestionMulti.isChecked = item.isMulti

        var choicesAdapter = choiceAdapters[item]
        if (choicesAdapter == null) {
            choicesAdapter = PollChoicesAdapter(context, item, poll)
            choiceAdapters[item] = choicesAdapter
        }

        holder.binding.pollQuestionChoices.adapter = choicesAdapter
    }

    inner class ViewHolder(
        val binding: EditPollQuestionBinding,
        val customTextWatcher: CustomTextWatcher,
        val checkedChangeListener: CustomCheckedChangeListener
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.pollQuestionTitleField.addTextChangedListener(customTextWatcher)
            binding.pollQuestionMulti.setOnCheckedChangeListener(checkedChangeListener)

            binding.pollQuestionChoices.layoutManager = LinearLayoutManager(
                binding.pollQuestionChoices.context
            )

            binding.pollAddChoice.setOnClickListener {
                val adapter = choiceAdapters[questions[layoutPosition]]
                adapter?.add(EditPoll.Choice())
            }

            binding.pollQuestionDelete.setOnClickListener {
                MaterialAlertDialogBuilder(it.context)
                    .setMessage(R.string.ask_delete_question)
                    .setPositiveButton(R.string.ok) { _, _ ->
                        val question = questions[layoutPosition]

                        if (question.index > poll.baseIndexOffset) {
                            val start = question.index
                            val end = poll.baseIndexOffset + poll.indexOffset
                            for (i in start..end) {
                                val q = EditPoll.findQuestionByIndex(poll, i)
                                q?.let {
                                    it.index = it.index - 1
                                }
                            }
                            poll.reduceIndexOffset()
                        }
                        val pos = layoutPosition
                        questions.remove(question)
                        choiceAdapters.remove(question)
                        notifyItemRemoved(pos)
                    }
                    .setNegativeButton(R.string.no, null)
                    .showWithStyledButtons()
            }
        }
    }

    inner class CustomTextWatcher : SimpleTextWatcher() {
        private var position: Int = 0

        fun updatePosition(position: Int) {
            this.position = position
        }

        override fun onTextChanged(charSequence: CharSequence, i: Int, i2: Int, i3: Int) {
            questions[position].title = charSequence.toString()
        }
    }

    inner class CustomCheckedChangeListener : CompoundButton.OnCheckedChangeListener {
        private var position: Int = 0

        fun updatePosition(position: Int) {
            this.position = position
        }

        override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
            questions[position].isMulti = isChecked
        }
    }
}
