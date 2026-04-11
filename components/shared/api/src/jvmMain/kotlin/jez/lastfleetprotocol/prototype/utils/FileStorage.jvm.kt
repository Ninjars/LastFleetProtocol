package jez.lastfleetprotocol.prototype.utils

import jez.lastfleetprotocol.prototype.utils.FileStorage.appDirs
import java.io.File

private val baseDir: File
    get() = File(appDirs.getUserDataDir())

actual fun saveFile(directory: String, name: String, content: String) {
    val dir = File(baseDir, directory)
    dir.mkdirs()
    File(dir, name).writeText(content)
}

actual fun loadFile(directory: String, name: String): String? {
    val file = File(File(baseDir, directory), name)
    return if (file.exists()) file.readText() else null
}

actual fun listFiles(directory: String): List<String> {
    val dir = File(baseDir, directory)
    return if (dir.exists() && dir.isDirectory) {
        dir.listFiles()?.map { it.name } ?: emptyList()
    } else {
        emptyList()
    }
}

actual fun deleteFile(directory: String, name: String) {
    val file = File(File(baseDir, directory), name)
    if (file.exists()) file.delete()
}
