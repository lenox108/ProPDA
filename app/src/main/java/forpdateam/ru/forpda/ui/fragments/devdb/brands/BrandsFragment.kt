package forpdateam.ru.forpda.ui.fragments.devdb.brands

import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import android.view.Menu
import android.view.MenuItem
import android.view.View
import com.google.android.material.chip.Chip

import java.util.ArrayList

import androidx.fragment.app.viewModels
import forpdateam.ru.forpda.R
import forpdateam.ru.forpda.entity.remote.devdb.Brands
import forpdateam.ru.forpda.presentation.devdb.brands.BrandsUiEvent
import forpdateam.ru.forpda.presentation.devdb.brands.BrandsViewModel
import forpdateam.ru.forpda.ui.fragments.RecyclerFragment
import forpdateam.ru.forpda.ui.views.adapters.BaseAdapter
import forpdateam.ru.forpda.ui.views.adapters.BaseSectionedAdapter
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch

/**
 * Created by radiationx on 08.08.17.
 */

@AndroidEntryPoint
class BrandsFragment : RecyclerFragment(), BaseSectionedAdapter.OnItemClickListener<Brands.Item> {

    override fun topBarSurfaceColorAttr(): Int = R.attr.main_toolbar_accent_surface

    private lateinit var adapter: BrandsAdapter

    private val presenter: BrandsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configuration.defaultTitle = getString(R.string.fragment_title_brands)
        arguments?.apply {
            getString(ARG_CATEGORY_ID)?.also {
                presenter.initCategory(it)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
        refreshLayout.setOnRefreshListener { presenter.loadBrands() }
        titlesWrapper.visibility = View.GONE
        toolbarFilterScroll.visibility = View.VISIBLE
        toolbarFilterChips.contentDescription = getString(R.string.devdb_category_picker_prompt)
        syncToolbarSpinnerEndSpacer()
        clearToolbarScrollFlags()

        adapter = BrandsAdapter()
        recyclerView.adapter = adapter

        adapter.setOnItemClickListener(this)

        presenter.start()
        observeViewModel()
    }

    override fun onDestroyView() {
        super.onDestroyView()
    }

    override fun isShadowVisible(): Boolean {
        return true
    }

    override fun addBaseToolbarMenu(menu: Menu) {
        super.addBaseToolbarMenu(menu)
        menu.add(R.string.fragment_title_device_search)
                .setIcon(R.drawable.ic_toolbar_search)
                .setOnMenuItemClickListener {
                    presenter.openSearch()
                    false
                }
                .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS)
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    presenter.refreshing.collect { isRefreshing ->
                        refreshLayout.isRefreshing = isRefreshing
                    }
                }
                launch {
                    presenter.uiEvents.collect { event ->
                        handleUiEvent(event)
                    }
                }
            }
        }
    }

    private fun handleUiEvent(event: BrandsUiEvent) {
        when (event) {
            is BrandsUiEvent.InitCategories -> initCategories(event.categories, event.position)
            is BrandsUiEvent.ShowData -> showData(event.brands)
        }
    }

    private fun initCategories(categories: Array<String>, position: Int) {
        toolbarFilterChips.removeAllViews()
        categories.forEachIndexed { index, category ->
            val chip = layoutInflater.inflate(
                    R.layout.toolbar_filter_chip, toolbarFilterChips, false) as Chip
            chip.id = View.generateViewId()
            chip.text = getCategoryTitle(category) ?: category
            chip.isChecked = index == position
            // singleSelection + selectionRequired в ChipGroup гарантируют, что отмечен
            // ровно один чип; клик по нему грузит свою категорию.
            chip.setOnClickListener {
                if (chip.isChecked) {
                    presenter.selectCategory(index)
                    presenter.loadBrands()
                }
            }
            toolbarFilterChips.addView(chip)
        }
    }

    private fun showData(data: Brands) {
        setTitle(data.catTitle)
        val newSections = data.letterMap.map { (key, value) -> android.util.Pair(key, value) }
        // Delay submit to prevent RecyclerView crash during layout
        recyclerView.post {
            adapter.submitSections(newSections)
        }
    }

    override fun onItemClick(item: Brands.Item) {
        presenter.openBrand(item)
    }

    override fun onItemLongClick(item: Brands.Item): Boolean {
        return false
    }

    private fun getCategoryTitle(category: String): String? {
        when (category) {
            BrandsViewModel.CATEGORY_PHONES -> return getString(R.string.brands_category_phones)
            BrandsViewModel.CATEGORY_PAD -> return getString(R.string.brands_category_tabs)
            BrandsViewModel.CATEGORY_EBOOK -> return getString(R.string.brands_category_ebook)
            BrandsViewModel.CATEGORY_SMARTWATCH -> return getString(R.string.brands_category_smartwatch)
        }
        return null
    }

    companion object {
        const val ARG_CATEGORY_ID = "CATEGORY_ID"
    }
}
