package com.apkupdater.fragment

import android.graphics.PorterDuff
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import com.apkupdater.R
import com.apkupdater.databinding.FragmentUpdatesBinding
import com.apkupdater.databinding.ViewAppsBinding
import com.apkupdater.model.ui.AppUpdate
import com.apkupdater.repository.googleplay.GooglePlayRepository
import com.apkupdater.util.adapter.ViewAppsAdapter
import com.apkupdater.util.app.AppPrefs
import com.apkupdater.util.app.InstallUtil
import com.apkupdater.util.getAccentColor
import com.apkupdater.util.iconUri
import com.apkupdater.util.ioScope
import com.apkupdater.util.launchUrl
import com.apkupdater.util.observe
import com.apkupdater.viewmodel.MainViewModel
import com.apkupdater.viewmodel.UpdatesViewModel
import com.bumptech.glide.Glide
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.sharedViewModel

class UpdatesFragment : Fragment() {
	private var _binding: FragmentUpdatesBinding? = null
	private val binding get() = _binding!!

	private val updatesViewModel: UpdatesViewModel by sharedViewModel()
	private val mainViewModel: MainViewModel by sharedViewModel()
	private val installer: InstallUtil by inject()
	private val prefs: AppPrefs by inject()
	private val googlePlayRepository: GooglePlayRepository by inject()

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
		_binding = FragmentUpdatesBinding.inflate(inflater, container, false)
		return binding.root
	}

	override fun onDestroyView() {
		super.onDestroyView()
		_binding = null
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		val adapter = ViewAppsAdapter(onBind)
		binding.run {
			recyclerView.layoutManager = LinearLayoutManager(context)
			recyclerView.adapter = adapter
			(recyclerView.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
		}
		updatesViewModel.items.observe(this) {
			it?.let {
				adapter.items = it
				mainViewModel.updatesBadge.postValue(it.size)
			}
		}
	}

	private val onBind = { itemBinding: ViewAppsBinding, app: AppUpdate ->
		itemBinding.runCatching {
			name.text = app.name
			packageName.text = app.packageName
			version.text = getString(R.string.update_version_version_code, app.oldVersion, app.oldCode, app.version, app.versionCode)
			actionOne.text = getString(R.string.action_install)
			if (app.loading) {
				progress.visibility = View.VISIBLE
				actionOne.visibility = View.INVISIBLE
			} else {
				progress.visibility = View.INVISIBLE
				actionOne.visibility = View.VISIBLE
				actionOne.text = getString(R.string.action_install)
				actionOne.setOnClickListener { if (app.url.endsWith("apk") || app.url == "play") downloadAndInstall(app) else launchUrl(app.url) }
			}
			source.setColorFilter(root.context.getAccentColor(), PorterDuff.Mode.MULTIPLY)
			Glide.with(root).load(app.source).into(source)
			Glide.with(root).load(iconUri(app.packageName, root.context.packageManager.getApplicationInfo(app.packageName, 0).icon)).into(icon)
		}.onFailure { Log.e("UpdatesFragment", "onBind", it) }.let {}
	}

	private fun downloadAndInstall(app: AppUpdate) = ioScope.launch {
		runCatching {
			updatesViewModel.setLoading(app.id, true)
			val url = if (app.url == "play") googlePlayRepository.getDownloadUrl(app.packageName, app.versionCode, app.oldCode) else app.url
			val file = installer.downloadAsync(requireActivity(), url) { _, _ -> updatesViewModel.setLoading(app.id, true) }
			if(installer.install(requireActivity(), file, app.id)) {
				updatesViewModel.setLoading(app.id, false)
				updatesViewModel.remove(app.id)
				mainViewModel.snackbar.postValue(getString(R.string.app_install_success))
			} else if (prefs.settings.rootInstall) {
				updatesViewModel.setLoading(app.id, false)
				mainViewModel.snackbar.postValue(getString(R.string.app_install_failure))
			}
		}.onFailure {
			updatesViewModel.setLoading(app.id, false)
			mainViewModel.snackbar.postValue(it.message ?: "downloadAndInstall error.")
		}
	}

}
