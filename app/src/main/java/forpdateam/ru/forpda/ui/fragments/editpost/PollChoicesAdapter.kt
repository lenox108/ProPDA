package forpdateam.ru.forpda.ui.fragments.editpost

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.simple.SimpleTextWatcher
import forpdateam.ru.forpda.databinding.EditPollChoiceBinding
import forpdateam.ru.forpda.entity.remote.editpost.EditPoll
import forpdateam.ru.forpda.ui.views.dialog.showWithStyledButtons

class PollChoicesAdapter(
    private val context: Context,
    private val question: EditPoll.Question,
    private val poll: EditPoll
) : RecyclerView.Adapter<PollChoicesAdapter.ViewHolder>() {

    private val choices: MutableList<EditPoll.Choice> = question.choices?.toMutableList() ?: mutableListOf()

    constructor(context: Context) : this(context, EditPoll.Question(), EditPoll()) {
        // Empty constructor for initialization without data
    }

    fun add(choice: EditPoll.Choice) {
        if (choices.size < poll.maxChoices) {
            question.increaseIndexOffset()
            choice.index = question.indexOffset + question.baseIndexOffset
            choices.add(choice)
            notifyItemInserted(choices.size - 1)
        } else {
            Toast.makeText(context, String.format(context.getString(R.string.poll_answers_Max), poll.maxChoices), Toast.LENGTH_SHORT).show()
        }
    }

    fun getItem(position: Int): EditPoll.Choice = choices[position]

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = EditPollChoiceBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding, MyCustomEditTextListener())
    }

    override fun getItemCount(): Int = choices.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(holder.adapterPosition)

        holder.myCustomEditTextListener.updatePosition(holder.adapterPosition)
        holder.binding.pollChoiceTitle.editText?.setText(item.title)
        holder.binding.pollChoiceTitle.hint = String.format(
            context.getString(R.string.poll_answer_Pos),
            holder.adapterPosition + 1
        )
    }

    inner class ViewHolder(
        val binding: EditPollChoiceBinding,
        val myCustomEditTextListener: MyCustomEditTextListener
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.pollChoiceTitle.editText?.addTextChangedListener(myCustomEditTextListener)
            binding.pollChoiceDelete.setOnClickListener {
                MaterialAlertDialogBuilder(itemView.context)
                    .setMessage(R.string.ask_delete_answer)
                    .setPositiveButton(R.string.ok) { _, _ ->
                        val choice = choices[layoutPosition]
                        if (choice.index > question.baseIndexOffset) {
                            val start = choice.index
                            val end = question.baseIndexOffset + question.indexOffset
                            for (i in start..end) {
                                val c = EditPoll.findChoiceByIndex(question, i)
                                c?.let {
                                    it.index = it.index - 1
                                }
                            }
                            question.reduceIndexOffset()
                        }
                        val pos = layoutPosition
                        choices.removeAt(pos)
                        notifyItemRemoved(pos)
                    }
                    .setNegativeButton(R.string.no, null)
                    .showWithStyledButtons()
            }
        }
    }

    inner class MyCustomEditTextListener : SimpleTextWatcher() {
        private var position: Int = 0

        fun updatePosition(position: Int) {
            this.position = position
        }

        override fun onTextChanged(charSequence: CharSequence, i: Int, i2: Int, i3: Int) {
            choices[position].title = charSequence.toString()
        }
    }
}
