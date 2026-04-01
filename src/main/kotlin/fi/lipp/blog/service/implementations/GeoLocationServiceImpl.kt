package fi.lipp.blog.service.implementations

import com.maxmind.geoip2.DatabaseReader
import fi.lipp.blog.service.GeoLocationService
import java.io.File
import java.net.InetAddress

class GeoLocationServiceImpl(databasePath: String) : GeoLocationService {
    private val reader: DatabaseReader? = try {
        val dbFile = File(databasePath)
        if (dbFile.exists()) {
            DatabaseReader.Builder(dbFile).build()
        } else {
            null
        }
    } catch (_: Exception) {
        null
    }

    override suspend fun resolveLocation(ip: String): String {
        if (reader == null) return "Unknown"
        return try {
            if (ip == "127.0.0.1" || ip == "localhost" || ip == "0:0:0:0:0:0:0:1") {
                return "Unknown"
            }
            val address = InetAddress.getByName(ip)
            val response = reader.city(address)
            val country = response.country?.name ?: ""
            val city = response.city?.name ?: ""
            when {
                country.isNotEmpty() && city.isNotEmpty() -> "$country, $city"
                country.isNotEmpty() -> country
                else -> "Unknown"
            }
        } catch (_: Exception) {
            "Unknown"
        }
    }
}
