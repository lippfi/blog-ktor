package fi.lipp.blog.service.implementations

import fi.lipp.blog.service.DatabaseSeeder
import org.slf4j.LoggerFactory

/**
 * Class responsible for initializing the database with all seeders during application startup.
 * This provides a centralized place for all database initialization logic.
 */
class DatabaseInitializer(private val seeders: List<DatabaseSeeder>) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    /**
     * Initializes the database with all registered seeders.
     * This method should be called during application startup.
     */
    fun initialize() {
        logger.info("Initializing database with seeders")
        seeders.forEach { seeder ->
            logger.info("Running seeder: ${seeder::class.java.simpleName}")
            seeder.seed()
        }
        logger.info("Database initialization completed")
    }
}