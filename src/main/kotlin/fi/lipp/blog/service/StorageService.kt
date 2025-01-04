package fi.lipp.blog.service

import fi.lipp.blog.data.BlogFile
import fi.lipp.blog.data.FileUploadData
import java.io.File
import java.net.URL
import java.util.*

interface StorageService {
    fun store(userId: UUID, files: List<FileUploadData>): List<BlogFile>
    fun storeAvatars(userId: UUID, files: List<FileUploadData>): List<BlogFile>
    
    fun getFile(file: BlogFile): File
    fun getFileURL(file: BlogFile): String
}