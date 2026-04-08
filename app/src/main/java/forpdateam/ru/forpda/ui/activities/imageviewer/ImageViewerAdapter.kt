package forpdateam.ru.forpda.ui.activities.imageviewer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.viewpager.widget.PagerAdapter
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.github.chrisbanes.photoview.OnPhotoTapListener
import com.github.chrisbanes.photoview.PhotoView
import com.github.rahatarmanahmed.cpv.CircularProgressView
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.common.ForPdaCoil

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
        val imageLayout = LayoutInflater
                .from(container.context)
                .inflate(R.layout.img_view_page, container, false)
        container.addView(imageLayout, 0)
        loadImage(container, imageLayout, position)
        return imageLayout
    }

    override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
        container.removeView(`object` as View)
    }

    override fun isViewFromObject(view: View, `object`: Any): Boolean = view == `object`

    private fun loadImage(container: ViewGroup, imageLayout: View, position: Int) {
        val progressBar = imageLayout.findViewById<CircularProgressView>(R.id.progress_bar)
        val photoView = imageLayout.findViewById<PhotoView>(R.id.photo_view)
        progressBar.visibility = View.VISIBLE
        val raw = items[position]
        val data = ForPdaCoil.normalizeData(raw)

        val request = ImageRequest.Builder(container.context)
                .data(data)
                .allowHardware(false)
                .target(photoView)
                .listener(object : ImageRequest.Listener {
                    override fun onStart(request: ImageRequest) {
                        progressBar.visibility = View.VISIBLE
                        if (progressBar.isIndeterminate) {
                            progressBar.isIndeterminate = false
                            progressBar.stopAnimation()
                        }
                    }

                    override fun onSuccess(request: ImageRequest, result: SuccessResult) {
                        progressBar.visibility = View.GONE
                    }

                    override fun onError(request: ImageRequest, result: ErrorResult) {
                        progressBar.visibility = View.GONE
                        Toast.makeText(container.context, R.string.error_occurred, Toast.LENGTH_SHORT).show()
                    }

                    override fun onCancel(request: ImageRequest) {
                        progressBar.visibility = View.GONE
                    }
                })
                .build()
        ForPdaCoil.imageLoader.enqueue(request)

        photoView.setOnPhotoTapListener(tapListener)
    }
}
