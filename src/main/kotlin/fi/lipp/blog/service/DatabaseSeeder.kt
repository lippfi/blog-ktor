package fi.lipp.blog.service

/**
 * Interface for database seeding operations.
 * Implementations of this interface are responsible for seeding the database with initial data.
 */
interface DatabaseSeeder {
    /**
     * Seeds the database with initial data.
     * This method should be called during application startup.
     */
    fun seed()
}