package forpdateam.ru.forpda.ui.views.messagepanel.inserthelper

import android.content.Context
import android.util.Pair
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.databinding.InsertHelperBodyBinding
import forpdateam.ru.forpda.databinding.InsertHelperItemBinding
import forpdateam.ru.forpda.ui.views.dialog.showWithStyledButtons

/**
 * Created by radiationx on 27.05.17.
 */
class InsertHelper(context: Context) {
    private val headers: ArrayList<Pair<String, String>> = ArrayList()
    private val headersLayout: ArrayList<EditText> = ArrayList()
    private var bodyLayout: EditText? = null
    private var body: Pair<String, String>? = null
    private var title: String? = null
    private val context: Context = context
    private val inflater: LayoutInflater =
        LayoutInflater.from(MaterialAlertDialogBuilder(context).context)
    private val layoutContainer: ScrollView
    private val itemsContainer: LinearLayout
    private var insertListener: InsertListener? = null

    private val bodyBinding: InsertHelperBodyBinding = InsertHelperBodyBinding.inflate(inflater, null, false)

    init {
        layoutContainer = bodyBinding.root
        itemsContainer = bodyBinding.insertHelperItemsContainer
    }

    fun addHeader(title: String, code: String?) {
        headers.add(Pair(title, code))
        val itemBinding = InsertHelperItemBinding.inflate(inflater, null, false)
        (itemBinding.root as TextInputLayout).hint = title
        headersLayout.add(itemBinding.insertHelperItemText)
        itemsContainer.addView(itemBinding.root)
    }

    fun setBody(title: String, value: String?) {
        this.body = Pair(title, value)
        val itemBinding = InsertHelperItemBinding.inflate(inflater, null, false)
        (itemBinding.root as TextInputLayout).hint = title
        itemBinding.insertHelperItemText.setText(value)
        bodyLayout = itemBinding.insertHelperItemText
        itemsContainer.addView(itemBinding.root)
    }

    fun setInsertListener(insertListener: InsertListener?) {
        this.insertListener = insertListener
    }

    fun show() {
        val alertDialog = MaterialAlertDialogBuilder(context)
            .setView(layoutContainer)
            .setPositiveButton(R.string.insert) { _, _ ->
                insertListener?.let { listener ->
                    val resultHeaders = ArrayList<Pair<String, String>>()
                    for (i in headers.indices) {
                        var value: String? = null
                        val editable = headersLayout[i].text
                        if (editable != null) {
                            value = editable.toString()
                            if (value.isEmpty()) {
                                value = null
                            }
                        }
                        resultHeaders.add(Pair(headers[i].second, value))
                    }
                    if (bodyLayout != null) {
                        listener.onInsert(resultHeaders, bodyLayout!!.text.toString())
                    } else {
                        listener.onInsert(resultHeaders, null)
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .showWithStyledButtons()
        alertDialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    interface InsertListener {
        fun onInsert(resultHeaders: ArrayList<Pair<String, String>>, bodyResult: String?)
    }
}
