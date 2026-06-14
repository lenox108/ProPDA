package forpdateam.ru.forpda.ui.activities.imageviewer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.viewpager.widget.PagerAdapter
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.github.chrisbanes.photoview.OnPhotoTapListener
import com.google.android.material.snackbar.Snackbar
import forpdateam.ru.forpda.BuildConfig
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.client.OkHttpResponseException
import forpdateam.ru.forpda.client.interceptors.ImageLoadingInterceptor
import coil.size.Precision
import forpdateam.ru.forpda.common.ForPdaCoil
import forpdateam.ru.forpda.common.FourPdaImageUrls
import forpdateam.ru.forpda.common.makeSnackbarAboveSystemBars
import forpdateam.ru.forpda.databinding.ImgViewPageBinding
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import timber.log.Timber

class ImageViewerAdapter : PagerAdapter() {

    private var tapListener: OnPhotoTapListener? = null
    private val items = mutableListOf<String>()

    fun setTapListener(tapListener: OnPhotoTapListener) {
        this.tapListener = tapListener
    }

    fun bindItem(newItems: List<String>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun getCount(): Int = items.size

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val binding = ImgViewPageBinding.inflate(LayoutInflater.from(container.context), container, false)
        container.addView(binding.root, 0)
        loadImage(container, binding, position)
        return binding.root
    }

    override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
        container.removeView(`object` as View)
    }

    override fun isViewFromObject(view: View, `object`: Any): Boolean = view == `object`

    private fun loadImage(container: ViewGroup, binding: ImgViewPageBinding, position: Int) {
        binding.progressBar.visibility = View.VISIBLE
        val raw = items[position]
        val data = ForPdaCoil.normalizeData(FourPdaImageUrls.resolveViewerUrl(raw))
        logLoadStart(data)

        val request = ImageRequest.Builder(container.context)
                .data(data)
                .precision(Precision.EXACT)
                .allowHardware(false)
                .target(binding.photoView)
                .listener(object : ImageRequest.Listener {
                    override fun onStart(request: ImageRequest) {
                        binding.progressBar.visibility = View.VISIBLE
                        if (binding.progressBar.isIndeterminate) {
                            binding.progressBar.isIndeterminate = false
                        }
                    }

                    override fun onSuccess(request: ImageRequest, result: SuccessResult) {
                        binding.progressBar.visibility = View.GONE
                    }

                    override fun onError(request: ImageRequest, result: ErrorResult) {
                        binding.progressBar.visibility = View.GONE
                        logLoadError(data, result.throwable)
                        container.makeSnackbarAboveSystemBars(errorMessageRes(result.throwable), Snackbar.LENGTH_LONG)
                                .setAction(R.string.retry) { loadImage(container, binding, position) }
                                .show()
                    }

                    override fun onCancel(request: ImageRequest) {
                        binding.progressBar.visibility = View.GONE
                    }
                })
                .build()
        ForPdaCoil.imageLoader.enqueue(request)

        binding.photoView.setOnPhotoTapListener(tapListener)
    }

    private fun errorMessageRes(throwable: Throwable): Int {
        val responseException = findResponseException(throwable)
        return when (responseException?.code) {
            in 500..599 -> R.string.image_viewer_server_unavailable
            401, 403 -> R.string.image_viewer_auth_required
            else -> R.string.error_occurred
        }
    }

    private fun findResponseException(throwable: Throwable?): OkHttpResponseException? {
        var current = throwable
        while (current != null) {
            if (current is OkHttpResponseException) return current
            current = current.cause
        }
        return null
    }

    private fun logLoadStart(data: String) {
        if (!BuildConfig.DEBUG) return
        val url = data.toHttpUrlOrNull() ?: return
        if (!ImageLoadingInterceptor.isFourPdaImageRequest(url)) return
        Timber.tag("ImageViewer").d(
            "imageRequestStart host=%s path=%s queryPresent=%s",
            url.host,
            url.encodedPath,
            url.encodedQuery != null
        )
    }

    private fun logLoadError(data: String, throwable: Throwable) {
        if (!BuildConfig.DEBUG) return
        val url = data.toHttpUrlOrNull() ?: return
        if (!ImageLoadingInterceptor.isFourPdaImageRequest(url)) return
        Timber.tag("ImageViewer").w(
            "imageRequestError host=%s path=%s type=%s code=%s",
            url.host,
            url.encodedPath,
            throwable::class.java.simpleName,
            findResponseException(throwable)?.code?.toString().orEmpty()
        )
    }
}
