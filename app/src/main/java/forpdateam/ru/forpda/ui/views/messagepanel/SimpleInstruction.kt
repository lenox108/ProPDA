package forpdateam.ru.forpda.ui.views.messagepanel

import android.content.Context
import android.view.LayoutInflater
import android.widget.ScrollView
import forpdateam.ru.forpda.databinding.MessagePanelInstructionBinding

/**
 * Created by radiationx on 26.05.17.
 */
class SimpleInstruction(context: Context) : ScrollView(context) {
    private var listener: OnClickListener? = null
    private val binding: MessagePanelInstructionBinding

    init {
        binding = MessagePanelInstructionBinding.inflate(LayoutInflater.from(context), this, false)
        setFillViewport(true)
        binding.instructionCloseButton.setOnClickListener { v ->
            this.visibility = GONE
            listener?.onClick(v)
        }
    }

    fun setText(text: String) {
        binding.instructionMessage.text = text
    }

    fun setOnCloseClick(listener: OnClickListener?) {
        this.listener = listener
    }
}
