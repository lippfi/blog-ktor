package fi.lipp.blog.service

import fi.lipp.blog.data.BlogFile
import fi.lipp.blog.data.FileUploadData
import java.io.InputStream
import java.util.*

interface StorageService {
    fun store(viewer: Viewer.Registered, files: List<FileUploadData>): List<BlogFile>
    fun storeAvatars(viewer: Viewer.Registered, files: List<FileUploadData>): List<BlogFile>
    fun storeReaction(viewer: Viewer.Registered, fileName: String, file: FileUploadData): BlogFile

    fun openFileStream(file: BlogFile): InputStream
    fun getFileURL(file: BlogFile): String
    fun getFileURLs(files: Collection<BlogFile>): Map<UUID, String>
    fun getStorageKeyByUrl(url: String): String
}
