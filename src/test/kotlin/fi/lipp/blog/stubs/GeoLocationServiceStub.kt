package fi.lipp.blog.stubs

import fi.lipp.blog.service.GeoLocationService

class GeoLocationServiceStub : GeoLocationService {
    override suspend fun resolveLocation(ip: String): String {
        return "Unknown"
    }
}
