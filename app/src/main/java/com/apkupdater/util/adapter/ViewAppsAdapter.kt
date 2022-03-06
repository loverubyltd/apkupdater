package com.apkupdater.util.adapter

import com.apkupdater.R
import com.apkupdater.databinding.ViewAppsBinding

class ViewAppsAdapter<AppModel : Id>(onBind: ViewAppsBinding.(AppModel) -> Unit) :
    BindAdapter<AppModel>(R.layout.view_apps, { view, model ->
        ViewAppsBinding.bind(view).also { onBind(it, model) }
    })
