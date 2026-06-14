package forpdateam.ru.forpda.ui.views.messagepanel.colorpicker

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.databinding.ColorPickerLayoutBinding
import forpdateam.ru.forpda.ui.views.dialog.showWithStyledButtons

/**
 * Created by radiationx on 27.05.17.
 */
class ColorPicker(context: Context, listener: ColorPaletteView.OnColorSelectedListener?) {
    private val titles = arrayOf("Material", "Forum")

    init {
        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val binding = ColorPickerLayoutBinding.inflate(inflater, null, false)

        val viewPager = binding.colorPickerPager

        val viewList = ArrayList<ScrollView>()
        val scrollView = ScrollView(context)
        val scrollView1 = ScrollView(context)
        val materialColors = ColorPaletteView(context)
        materialColors.setColors(context.resources.getIntArray(R.array.md_colors))
        val forumColors = ColorPaletteView(context)
        forumColors.setColors(context.resources.getIntArray(R.array.forum_colors))
        scrollView.addView(materialColors)
        scrollView1.addView(forumColors)
        viewList.add(scrollView)
        viewList.add(scrollView1)

        viewPager.adapter = MyPagerAdapter(viewList)
        binding.colorPickerTabLayout.setupWithViewPager(viewPager)
        val dialog = MaterialAlertDialogBuilder(context)
            .setView(binding.root)
            .showWithStyledButtons()
        val mainListener = object : ColorPaletteView.OnColorSelectedListener {
            override fun onColorSelected(color: Int) {
                listener?.onColorSelected(color)
                dialog.dismiss()
            }
        }
        materialColors.setOnColorSelectedListener(mainListener)
        forumColors.setOnColorSelectedListener(mainListener)
    }

    private inner class MyPagerAdapter(private val pages: List<ScrollView>) : PagerAdapter() {
        override fun instantiateItem(container: ViewGroup, position: Int): Any {
            val v = pages[position]
            container.addView(v, 0)
            return v
        }

        override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
            container.removeView(`object` as View)
        }

        override fun getCount(): Int = pages.size

        override fun isViewFromObject(view: View, `object`: Any): Boolean = view == `object`

        override fun getPageTitle(position: Int): CharSequence = titles[position]
    }
}
