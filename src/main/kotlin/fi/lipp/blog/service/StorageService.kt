package fi.lipp.blog.service

import fi.lipp.blog.data.BlogFile
import fi.lipp.blog.data.FileUploadData
import java.net.URL

interface StorageService {
    fun store(userId: Long, files: List<FileUploadData>): List<BlogFile>
    fun storeAvatars(userId: Long, files: List<FileUploadData>): List<BlogFile>

    fun getFileURL(file: BlogFile): URL
}