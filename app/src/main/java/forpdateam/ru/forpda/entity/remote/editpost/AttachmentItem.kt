package forpdateam.ru.forpda.entity.remote.editpost
import forpdateam.ru.forpda.BuildConfig
import timber.log.Timber

import android.os.Parcel
import android.os.Parcelable
import forpdateam.ru.forpda.model.data.remote.IWebClient
import java.util.regex.Pattern

/**
 * Created by radiationx on 09.01.17.
 */
class AttachmentItem : Parcelable {

    companion object {
        private val imageExtensions = Pattern.compile("gif|jpg|jpeg|png", Pattern.CASE_INSENSITIVE)
        const val TYPE_FILE = 0
        const val TYPE_IMAGE = 1
        const val STATE_NOT_LOADED = 0
        const val STATE_LOADING = 1
        const val STATE_LOADED = 2
        const val STATUS_REMOVED = 0
        const val STATUS_NO_FILE = 1
        const val STATUS_UPLOADED = 2
        const val STATUS_READY = 3
        const val STATUS_UNKNOWN = 4

        @JvmField
        val CREATOR = object : Parcelable.Creator<AttachmentItem> {
            override fun createFromParcel(source: Parcel): AttachmentItem = AttachmentItem(source)
            override fun newArray(size: Int): Array<AttachmentItem?> = arrayOfNulls(size)
        }
    }

    var isError: Boolean = false
        internal set
    var selected: Boolean = false
        internal set

    var id: Int = -1
    var name: String? = null
    var extension: String? = null
        set(value) { field = value; if (value != null && imageExtensions.matcher(value).matches()) typeFile = TYPE_IMAGE }
    var weight: String? = null
    var typeFile: Int = TYPE_FILE
    var loadState: Int = STATE_LOADING
    var status: Int = STATUS_READY
    var imageUrl: String? = null
    var isErr: Boolean = false
        set(value) { field = value; isError = value }
        get() = isError

    fun setError(error: Boolean) { isErr = error }
    var errorText: String? = null
    var url: String? = null
    var width: Int = 0
    var height: Int = 0
    var md5: String? = null
    var progress: Int = -1
        private set

    internal var progressListener: IWebClient.ProgressListener? = null

    internal val itemProgressListener = object : IWebClient.ProgressListener {
        override fun onProgress(percent: Int) {
            this@AttachmentItem.progress = percent
            progressListener?.onProgress(percent)
        }
    }

    constructor(name: String?) { this.name = name }
    constructor()

    fun getItemProgressListener(): IWebClient.ProgressListener = itemProgressListener

    fun getProgressListener(): IWebClient.ProgressListener? = progressListener
    fun setProgressListener(progressListener: IWebClient.ProgressListener?) { this.progressListener = progressListener }

    fun toggle() { selected = !selected }

    override fun describeContents(): Int = 0

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        if (BuildConfig.DEBUG) Timber.d("AttachmentItem writeToParcel")
        parcel.writeByte((if (isError) 1 else 0).toByte())
        parcel.writeByte((if (selected) 1 else 0).toByte())
        parcel.writeInt(id)
        parcel.writeInt(typeFile)
        parcel.writeInt(loadState)
        parcel.writeInt(status)
        parcel.writeInt(progress)
        writeStringToParcel(parcel, name)
        writeStringToParcel(parcel, extension)
        writeStringToParcel(parcel, weight)
        writeStringToParcel(parcel, imageUrl)
        writeStringToParcel(parcel, url)
        writeStringToParcel(parcel, errorText)
    }

    private constructor(parcel: Parcel) {
        isError = parcel.readByte().toInt() != 0
        selected = parcel.readByte().toInt() != 0
        id = parcel.readInt()
        typeFile = parcel.readInt()
        loadState = parcel.readInt()
        status = parcel.readInt()
        progress = parcel.readInt()
        name = readStringFromParcel(parcel)
        extension = readStringFromParcel(parcel)
        weight = readStringFromParcel(parcel)
        imageUrl = readStringFromParcel(parcel)
        url = readStringFromParcel(parcel)
        errorText = readStringFromParcel(parcel)
    }

    private fun writeStringToParcel(parcel: Parcel, string: String?) {
        parcel.writeByte((if (string != null) 1 else 0).toByte())
        parcel.writeString(string)
    }

    private fun readStringFromParcel(parcel: Parcel): String? =
        if (parcel.readByte().toInt() != 0) parcel.readString() else null
}
