package fi.lipp.blog.service

interface GeoLocationService {
    /**
     * Resolves an IP address to a "Country, City" location string.
     * @param ip The IP address to resolve
     * @return A location string in "Country, City" format, or "Unknown" if resolution fails
     */
    suspend fun resolveLocation(ip: String): String
}
