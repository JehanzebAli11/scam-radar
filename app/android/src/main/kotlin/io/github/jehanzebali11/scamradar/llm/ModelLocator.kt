package io.github.jehanzebali11.scamradar.llm

import android.content.Context
import java.io.File

/**
 * Where the app looks for the exported model. For now it is pushed with adb
 * (see app/README.md); nothing is downloaded and nothing is bundled in the APK.
 *
 * 1. `/sdcard/Android/data/<package>/files/scamradar.litertlm` (app-specific
 *    external storage: `adb push` can write here on most devices, and the app
 *    needs no storage permission to read it).
 * 2. `<internal files dir>/scamradar.litertlm` (debug builds: copy in with
 *    `adb shell run-as <package> ...`).
 */
object ModelLocator {
    const val FILE_NAME = "scamradar.litertlm"

    fun candidates(context: Context): List<File> =
        listOfNotNull(context.getExternalFilesDir(null), context.filesDir).map { File(it, FILE_NAME) }

    fun find(context: Context): File? = candidates(context).firstOrNull { it.isFile && it.length() > 0 }
}
