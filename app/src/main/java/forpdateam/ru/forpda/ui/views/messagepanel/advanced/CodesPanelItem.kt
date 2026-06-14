package forpdateam.ru.forpda.ui.views.messagepanel.advanced

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.text.TextUtils
import android.util.Pair
import android.view.LayoutInflater
import android.widget.Button
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import androidx.recyclerview.widget.ItemTouchHelper
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.simple.SimpleTextWatcher
import forpdateam.ru.forpda.databinding.ReportLayoutBinding
import forpdateam.ru.forpda.model.preferences.OtherPreferencesHolder
import forpdateam.ru.forpda.ui.views.messagepanel.MessagePanel
import forpdateam.ru.forpda.ui.views.messagepanel.SimpleInstruction
import forpdateam.ru.forpda.ui.views.messagepanel.advanced.adapters.ItemDragCallback
import forpdateam.ru.forpda.ui.views.messagepanel.advanced.adapters.PanelItemAdapter
import forpdateam.ru.forpda.ui.views.messagepanel.colorpicker.ColorPaletteView
import forpdateam.ru.forpda.ui.views.messagepanel.colorpicker.ColorPicker
import forpdateam.ru.forpda.ui.views.messagepanel.inserthelper.InsertHelper
import forpdateam.ru.forpda.ui.views.dialog.showWithStyledButtons
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Created by radiationx on 08.01.17.
 */
@SuppressLint("ViewConstructor")
class CodesPanelItem(context: Context, panel: MessagePanel, private val otherPreferencesHolder: OtherPreferencesHolder) :
    BasePanelItem(context, panel, context.getString(R.string.codes_title)) {

    companion object {
        private var colors: MutableMap<String, String>? = null
    }

    private val openedCodes: MutableList<String> = mutableListOf()
    private val viewScope = MainScope()
    private val codeItems: MutableList<ButtonData> = getCodes()

    private val clickListener = object : PanelItemAdapter.OnItemClickListener {
        override fun onItemClick(item: ButtonData) {
            when (item.text) {
                "URL" -> urlInsert(item)
                "QUOTE" -> quoteInsert(item)
                "CODE" -> codeInsert(item)
                "SPOILER" -> spoilerInsert(item)
                "LIST" -> listInsert(item, false)
                "NUMLIST" -> listInsert(item, true)
                "COLOR" -> colorInsert(item)
                "BACKGROUND" -> colorInsert(item)
                "SIZE" -> sizeInsert(item)
                "FONT" -> fontInsert(item)
                else -> simpleInsertText(item)
            }
        }
    }

    init {
        val adapter = PanelItemAdapter(codeItems, emptyList(), PanelItemAdapter.TYPE_DRAWABLE)
        adapter.setOnItemClickListener(clickListener)
        recyclerView.setColumnWidth((96 * context.resources.displayMetrics.density).toInt())
        val touchHelper = ItemTouchHelper(ItemDragCallback(adapter))
        touchHelper.attachToRecyclerView(recyclerView)
        recyclerView.adapter = adapter

        if (otherPreferencesHolder.getTooltipMessagePanelSortingSync()) {
            val instruction = SimpleInstruction(getContext())
            instruction.setText(getContext().getString(R.string.code_panel_instruction))
            instruction.setOnCloseClick {
                viewScope.launch { otherPreferencesHolder.setTooltipMessagePanelSorting(false) }
            }
            addView(instruction)
        }
    }

    private fun listInsert(item: ButtonData, num: Boolean) {
        val selected = messagePanel.getSelectedText()
        val listLines = mutableListOf<String>()
        val tag = "LIST"
        if (selected.isNotEmpty()) {
            MaterialAlertDialogBuilder(getContext())
                .setMessage(R.string.transform_string_to_list)
                .setPositiveButton(R.string.ok) { _, _ ->
                    val lines = TextUtils.split(selected, "\n")
                    listLines.addAll(lines)
                    messagePanel.deleteSelected()
                }
                .setNegativeButton(R.string.no, null)
                .setOnDismissListener { listInsert(tag, num, listLines) }
                .showWithStyledButtons()
        } else {
            listInsert(tag, num, listLines)
        }
    }

    private fun listInsert(tag: String, num: Boolean, listLines: MutableList<String>) {
        val builder = MaterialAlertDialogBuilder(getContext())
        val binding = ReportLayoutBinding.inflate(LayoutInflater.from(builder.context))
        val i = intArrayOf(listLines.size + 1)
        binding.reportInputLayout.hint = String.format(
            getContext().getString(R.string.codes_list_item_Pos), i[0]
        )
        val alertDialog = builder
            .setView(binding.root)
            .setPositiveButton(R.string.add, null)
            .setNegativeButton(R.string.close) { _, _ ->
                val body = StringBuilder()
                for (line in listLines) {
                    body.append("[*]").append(line).append('\n')
                }
                val resultHeaders = arrayListOf<Pair<String, String>>()
                if (num) resultHeaders.add(Pair("", "1"))
                val bbcodes = createBbCode(tag, resultHeaders, body.toString())
                messagePanel.insertText(bbcodes[0], bbcodes[1], false)
            }
            .showWithStyledButtons()
        val positiveButton = alertDialog.getButton(DialogInterface.BUTTON_POSITIVE)
        positiveButton.isEnabled = false
        positiveButton.setOnClickListener {
            i[0]++
            listLines.add(binding.reportTextField.text.toString())
            binding.reportTextField.setText("")
            binding.reportInputLayout.hint = String.format(
                getContext().getString(R.string.codes_list_item_Pos), i[0]
            )
        }
        binding.reportTextField.addTextChangedListener(object : SimpleTextWatcher() {
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                positiveButton.isEnabled = s.isNotEmpty()
            }
        })
    }

    private fun colorInsert(item: ButtonData) {
        ColorPicker(getContext(), object : ColorPaletteView.OnColorSelectedListener {
            override fun onColorSelected(color: Int) {
                var colorStr = Integer.toHexString(color).uppercase()
                if (colorStr.length > 6) colorStr = colorStr.substring(2)
                colorStr = "#$colorStr"
                colorStr = getHtmlColor(colorStr)
                val resultHeaders = arrayListOf<Pair<String, String>>()
                resultHeaders.add(Pair("", colorStr))
                val bbcodes = createBbCode(item.text, resultHeaders, null)
                messagePanel.insertText(bbcodes[0], bbcodes[1])
            }
        })
    }

    private fun sizeInsert(item: ButtonData) {
        val items = arrayOf(
            "1 (8pt)", "2 (10pt)", "3 (12pt)", "4 (14pt)",
            "5 (18pt)", "6 (24pt)", "7 (36pt)"
        )
        for (i in items.indices) {
            items[i] = String.format(getContext().getString(R.string.codes_text_size_item_Size), items[i])
        }
        MaterialAlertDialogBuilder(getContext())
            .setTitle(R.string.codes_text_size)
            .setItems(items) { dialog, which ->
                val resultHeaders = arrayListOf<Pair<String, String>>()
                resultHeaders.add(Pair("", (which + 1).toString()))
                val bbcodes = createBbCode(item.text, resultHeaders, null)
                messagePanel.insertText(bbcodes[0], bbcodes[1])
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .showWithStyledButtons()
    }

    private fun fontInsert(item: ButtonData) {
        val selected = messagePanel.getSelectedText()
        val range = messagePanel.selectionRange
        val insertHelper = InsertHelper(getContext())
        insertHelper.addHeader(getContext().getString(R.string.codes_font), null)
        if (selected.isEmpty())
            insertHelper.setBody(getContext().getString(R.string.codes_font_text), null)
        insertHelper.setInsertListener(object : InsertHelper.InsertListener {
            override fun onInsert(resultHeaders: ArrayList<Pair<String, String>>, bodyResult: String?) {
                val bbcodes = createBbCode(item.text, resultHeaders, bodyResult)
                messagePanel.insertText(bbcodes[0], bbcodes[1], range[0], range[1])
            }
        })
        insertHelper.show()
    }

    private fun urlInsert(item: ButtonData) {
        val selected = messagePanel.getSelectedText()
        val range = messagePanel.selectionRange
        val insertHelper = InsertHelper(getContext())
        insertHelper.addHeader(getContext().getString(R.string.codes_link), null)
        if (selected.isEmpty())
            insertHelper.setBody(getContext().getString(R.string.codes_link_text), null)
        insertHelper.setInsertListener(object : InsertHelper.InsertListener {
            override fun onInsert(resultHeaders: ArrayList<Pair<String, String>>, bodyResult: String?) {
                val bbcodes = createBbCode(item.text, resultHeaders, bodyResult)
                messagePanel.insertText(bbcodes[0], bbcodes[1], range[0], range[1])
            }
        })
        insertHelper.show()
    }

    private fun spoilerInsert(item: ButtonData) {
        val selected = messagePanel.getSelectedText()
        val range = messagePanel.selectionRange
        val insertHelper = InsertHelper(getContext())
        insertHelper.addHeader(getContext().getString(R.string.codes_block_title), null)
        if (selected.isEmpty())
            insertHelper.setBody(getContext().getString(R.string.codes_spoiler_text), null)
        insertHelper.setInsertListener(object : InsertHelper.InsertListener {
            override fun onInsert(resultHeaders: ArrayList<Pair<String, String>>, bodyResult: String?) {
                val bbcodes = createBbCode(item.text, resultHeaders, bodyResult)
                messagePanel.insertText(bbcodes[0], bbcodes[1], range[0], range[1])
            }
        })
        insertHelper.show()
    }

    private fun codeInsert(item: ButtonData) {
        val selected = messagePanel.getSelectedText()
        val range = messagePanel.selectionRange
        val insertHelper = InsertHelper(getContext())
        insertHelper.addHeader(getContext().getString(R.string.codes_block_title), null)
        if (selected.isEmpty())
            insertHelper.setBody(getContext().getString(R.string.codes_code_text), null)
        insertHelper.setInsertListener(object : InsertHelper.InsertListener {
            override fun onInsert(resultHeaders: ArrayList<Pair<String, String>>, bodyResult: String?) {
                val bbcodes = createBbCode(item.text, resultHeaders, bodyResult)
                messagePanel.insertText(bbcodes[0], bbcodes[1], range[0], range[1])
            }
        })
        insertHelper.show()
    }

    private fun quoteInsert(item: ButtonData) {
        val selected = messagePanel.getSelectedText()
        val range = messagePanel.selectionRange
        val insertHelper = InsertHelper(getContext())
        insertHelper.addHeader(getContext().getString(R.string.codes_block_title), "name")
        if (selected.isEmpty())
            insertHelper.setBody(getContext().getString(R.string.codes_quote_text), null)
        insertHelper.setInsertListener(object : InsertHelper.InsertListener {
            override fun onInsert(resultHeaders: ArrayList<Pair<String, String>>, bodyResult: String?) {
                val bbcodes = createBbCode(item.text, resultHeaders, bodyResult)
                messagePanel.insertText(bbcodes[0], bbcodes[1], range[0], range[1])
            }
        })
        insertHelper.show()
    }

    private fun createBbCode(tag: String, headers: ArrayList<Pair<String, String>>?, body: String?): Array<String> {
        val start = StringBuilder("[$tag")
        if (headers != null) {
            for (header in headers) {
                if (header.first.isNullOrEmpty() && !header.second.isNullOrEmpty()) {
                    start.append("=").append(header.second)
                    break
                }
            }
            for (header in headers) {
                if (header.first.isNullOrEmpty() || header.second.isNullOrEmpty()) continue
                start.append(" ").append(header.first).append("=\"").append(header.second).append("\"")
            }
        }
        start.append("]")
        if (body != null) start.append(body)
        val end = "[/$tag]"
        return arrayOf(start.toString(), end)
    }

    private fun simpleInsertText(item: ButtonData) {
        val bbcodes = createBbCode(item.text, null, null)
        messagePanel.insertText(bbcodes[0], bbcodes[1])
    }

    override fun onDetachedFromWindow() {
        val savedOrder = codeItems.map { it.text }.joinToString(",")
        if (savedOrder.isNotEmpty()) {
            viewScope.launch {
                otherPreferencesHolder.setMessagePanelBbCodes(savedOrder)
            }.invokeOnCompletion {
                viewScope.cancel()
            }
        } else {
            viewScope.cancel()
        }
        super.onDetachedFromWindow()
    }

    private fun getCodes(): MutableList<ButtonData> {
        val tempCodes = arrayListOf<ButtonData>()
        tempCodes.add(ButtonData("B", R.drawable.ic_code_bold, getContext().getString(R.string.codes_name_bold)))
        tempCodes.add(ButtonData("I", R.drawable.ic_code_italic, getContext().getString(R.string.codes_name_italic)))
        tempCodes.add(ButtonData("U", R.drawable.ic_code_underline, getContext().getString(R.string.codes_name_underline)))
        tempCodes.add(ButtonData("S", R.drawable.ic_code_s, getContext().getString(R.string.codes_name_s)))
        tempCodes.add(ButtonData("URL", R.drawable.ic_code_url, getContext().getString(R.string.codes_name_link)))
        tempCodes.add(ButtonData("SPOILER", R.drawable.ic_code_spoiler, getContext().getString(R.string.codes_name_spoiler)))
        tempCodes.add(ButtonData("OFFTOP", R.drawable.ic_code_offtop, getContext().getString(R.string.codes_name_offtop)))
        tempCodes.add(ButtonData("QUOTE", R.drawable.ic_code_quote, getContext().getString(R.string.codes_name_quote)))
        tempCodes.add(ButtonData("CODE", R.drawable.ic_code_code, getContext().getString(R.string.codes_name_code)))
        tempCodes.add(ButtonData("COLOR", R.drawable.ic_code_color, getContext().getString(R.string.codes_name_text_color)))
        tempCodes.add(ButtonData("SIZE", R.drawable.ic_code_size, getContext().getString(R.string.codes_name_text_size)))
        tempCodes.add(ButtonData("FONT", R.drawable.ic_code_font, getContext().getString(R.string.codes_name_font)))
        tempCodes.add(ButtonData("HIDE", R.drawable.ic_code_hide, getContext().getString(R.string.codes_name_hide)))
        tempCodes.add(ButtonData("BACKGROUND", R.drawable.ic_code_background, getContext().getString(R.string.codes_name_bg_color)))
        tempCodes.add(ButtonData("LIST", R.drawable.ic_code_list, getContext().getString(R.string.codes_name_list)))
        tempCodes.add(ButtonData("NUMLIST", R.drawable.ic_code_numlist, getContext().getString(R.string.codes_name_numlist)))
        tempCodes.add(ButtonData("LEFT", R.drawable.ic_code_left, getContext().getString(R.string.codes_name_left)))
        tempCodes.add(ButtonData("CENTER", R.drawable.ic_code_center, getContext().getString(R.string.codes_name_center)))
        tempCodes.add(ButtonData("RIGHT", R.drawable.ic_code_right, getContext().getString(R.string.codes_name_right)))
        tempCodes.add(ButtonData("SUB", R.drawable.ic_code_sub, getContext().getString(R.string.codes_name_sub)))
        tempCodes.add(ButtonData("SUP", R.drawable.ic_code_sup, getContext().getString(R.string.codes_name_sup)))
        tempCodes.add(ButtonData("CUR", R.drawable.ic_code_cur, getContext().getString(R.string.codes_name_curator)))

        val sorted = otherPreferencesHolder.getMessagePanelBbCodesSync()
        if (sorted.isBlank()) {
            return tempCodes
        }

        val sortedCodes = TextUtils.split(sorted, ",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        val defaultByText = tempCodes.associateBy { it.text }
        val sortedItems = sortedCodes.mapNotNull { defaultByText[it] }
        val isSavedOrderValid = sortedCodes.size == tempCodes.size &&
                sortedCodes.toSet().size == tempCodes.size &&
                sortedItems.size == tempCodes.size

        if (!isSavedOrderValid) {
            viewScope.launch { otherPreferencesHolder.deleteMessagePanelBbCodes() }
            return tempCodes
        }

        return sortedItems.toMutableList()
    }

    private fun getHtmlColor(hexColor: String): String {
        if (colors == null) {
            colors = mutableMapOf(
                "#000000" to "black", "#FFFFFF" to "white", "#82CEE8" to "skyblue",
                "#426AE6" to "royalblue", "#0000FF" to "blue", "#07008C" to "darkblue",
                "#FDA500" to "orange", "#FF4300" to "orangered", "#E1133A" to "crimson",
                "#FF0000" to "red", "#8C0000" to "darkred", "#008000" to "green",
                "#41A317" to "limegreen", "#4E8975" to "seagreen", "#F52887" to "deeppink",
                "#FF6245" to "tomato", "#F76541" to "coral", "#800080" to "purple",
                "#440087" to "indigo", "#E3B382" to "burlywood", "#EE9A4D" to "sandybrown",
                "#C35817" to "sienna", "#C85A17" to "chocolate", "#037F81" to "teal",
                "#C0C0C0" to "silver", "#808080" to "gray"
            )
        }
        return colors!![hexColor] ?: hexColor
    }
}
