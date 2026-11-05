package com.recast.app.data.engine.office

import android.content.Context
import java.io.File

/** Se este APK traz o LibreOffice (builds sem acesso ao F-Droid, ou arquiteturas sem ele, não trazem). */
object OfficeAvailability {
    fun isAvailable(context: Context): Boolean =
        File(context.applicationInfo.nativeLibraryDir, "liblo-native-code.so").isFile
}
