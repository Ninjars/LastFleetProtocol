package jez.lastfleetprotocol.prototype.utils

expect fun saveFile(directory: String, name: String, content: String)
expect fun loadFile(directory: String, name: String): String?
expect fun listFiles(directory: String): List<String>
expect fun deleteFile(directory: String, name: String)
