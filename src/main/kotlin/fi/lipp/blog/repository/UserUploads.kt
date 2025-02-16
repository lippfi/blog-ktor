package fi.lipp.blog.repository

import kotlinx.datetime.LocalDate
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.kotlin.datetime.date

object UserUploads : UUIDTable() {
    val user = reference("user", Users, onDelete = ReferenceOption.CASCADE)
    val date = date("date")
    val totalBytes = long("total_bytes").default(0)

    init {
        uniqueIndex("idx_user_upload_date", user, date)
    }
}