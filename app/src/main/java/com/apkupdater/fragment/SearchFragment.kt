package com.apkupdater.fragment

import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import com.apkupdater.R
import com.apkupdater.databinding.FragmentSearchBinding
import com.apkupdater.databinding.ViewAppsBinding

import com.apkupdater.model.ui.AppSearch
import com.apkupdater.model.ui.AppUpdate
import com.apkupdater.repository.SearchRepository
import com.apkupdater.repository.googleplay.GooglePlayRepository
import com.apkupdater.util.*
import com.apkupdater.util.adapter.ViewAppsAdapter
import com.apkupdater.util.app.AppPrefs
import com.apkupdater.util.app.InstallUtil
import com.apkupdater.viewmodel.MainViewModel
import com.apkupdater.viewmodel.SearchViewModel
import com.bumptech.glide.Glide
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.sharedViewModel

class SearchFragment : Fragment() {
	private var _binding: FragmentSearchBinding? = null
	private val binding get() = _binding!!

	private val searchViewModel: SearchViewModel by sharedViewModel()
	private val mainViewModel: MainViewModel by sharedViewModel()
	private val searchRepository: SearchRepository by inject()
	private val googlePlayRepository: GooglePlayRepository by inject()
	private val installer: InstallUtil by inject()
	private val prefs: AppPrefs by inject()

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
		_binding = FragmentSearchBinding.inflate(inflater, container, false)
		return binding.root
	}

	override fun onDestroyView() {
		super.onDestroyView()
		_binding = null
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		// RecyclerView
		val adapter = ViewAppsAdapter(onBind)
		binding.run {
			recyclerView.layoutManager = LinearLayoutManager(context)
			recyclerView.adapter = adapter
			(recyclerView.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
		}
		searchViewModel.items.observe(this) {
			it?.let {
				adapter.items = it
				mainViewModel.searchBadge.postValue(it.size)
			}
		}

		// Search
		binding.text.setOnEditorActionListener { text, id, _ ->
			if (id == EditorInfo.IME_ACTION_SEARCH) {
				searchViewModel.search.postValue(text.text.toString())
				val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
				imm.hideSoftInputFromWindow(text.windowToken, 0)
				true
			} else {
				false
			}
		}

		searchViewModel.search.observe(this) { search(it) }
	}

	private fun search(text: String) = ioScope.launch {
		mainViewModel.loading.postValue(true)
		searchRepository.getSearchResultsAsync(text).await().fold(
			onSuccess = { searchViewModel.items.postValue(it) },
			onFailure = {
				mainViewModel.snackbar.postValue(it.message ?: "search error.")
				Log.e("SearchFragment", "search", it)
			}
		)
	}.invokeOnCompletion { mainViewModel.loading.postValue(false) }

	private val onBind = { itemBinding: ViewAppsBinding, app: AppSearch ->
		itemBinding.run {
			app.iconurl.ifNotEmpty { Glide.with(root).load(it).placeholder(ColorDrawable(Color.BLACK)).error(ColorDrawable(Color.RED)).into(icon) }
			name.text = app.name
			packageName.text = app.developer

			if (app.loading) {
				progress.visibility = View.VISIBLE
				actionOne.visibility = View.INVISIBLE
			} else {
				progress.visibility = View.INVISIBLE
				actionOne.visibility = View.VISIBLE
				actionOne.text = getString(R.string.action_install)
				actionOne.setOnClickListener { if (app.url.endsWith("apk") || app.url == "play")  downloadAndInstall(app) else launchUrl(app.url) }
			}
			Glide.with(root).load(app.source).into(source)
			source.setColorFilter(root.context.getAccentColor(), PorterDuff.Mode.MULTIPLY)
		}
	}

	private fun downloadAndInstall(app: AppSearch) = ioScope.launch {
		runCatching {
			searchViewModel.setLoading(app.id, true)
			val url = if (app.url == "play") googlePlayRepository.getDownloadUrl(app.packageName, app.versionCode, 0) else app.url
			val file = installer.downloadAsync(requireActivity(), url) { _, _ -> searchViewModel.setLoading(app.id, true) }
			if(installer.install(requireActivity(), file, app.id)) {
				searchViewModel.setLoading(app.id, false)
				searchViewModel.remove(app.id)
				mainViewModel.snackbar.postValue(getString(R.string.app_install_success))
			} else if (prefs.settings.rootInstall) {
				searchViewModel.setLoading(app.id, false)
				mainViewModel.snackbar.postValue(getString(R.string.app_install_failure))
			}
		}.onFailure {
			searchViewModel.setLoading(app.id, false)
			mainViewModel.snackbar.postValue(it.message ?: "downloadAndInstall failure.")
		}
	}

}
