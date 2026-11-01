package com.convertia.app.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.convertia.app.R

/** Abas da barra de navegação inferior, na ordem em que aparecem. */
enum class Destination(
    @param:StringRes val label: Int,
    @param:DrawableRes val icon: Int,
    @param:StringRes val subtitle: Int,
) {
    Download(R.string.tab_download, R.drawable.ic_nav_download, R.string.download_subtitle),
    Convert(R.string.tab_convert, R.drawable.ic_nav_convert, R.string.convert_subtitle),
    Queue(R.string.tab_queue, R.drawable.ic_nav_queue, R.string.queue_subtitle),
    Library(R.string.tab_library, R.drawable.ic_nav_library, R.string.library_subtitle),
    Settings(R.string.tab_settings, R.drawable.ic_nav_settings, R.string.settings_subtitle),
}
