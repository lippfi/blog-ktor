package fi.lipp.blog

import fi.lipp.blog.plugins.*
import fi.lipp.blog.service.*
import fi.lipp.blog.service.implementations.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*
import org.koin.core.KoinApplication
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

// Restrict reactions to posts
// Settings
// Profile page
// Communities
// Allow post owners to delete comments of other users
// TODO what about Pages? Are they ready? Should I have start page or some order on them? Should pages have visibility? Own links?
// Ignore list
// Custom website design css
// Multiple diary styles (switch between them)
// Device sessions && token invalidation
// Online and activity
// TODO telegram integration

// POOL
// Cascade delete and stuff
// TODO API documentation
// TODO Integration tests
// TODO Cors

// THIRD ITERATION
// Caches
// background music
// TODO diary backup & diary restore
// TODO post backups & drafts : 1

// LOWEST PRIORITY
// TODO moderation
// TODO banned users
// TODO todo lists : 8
// TODO more application properties (invite code valid time, time before regeneration codes etc) : 1
fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

@Suppress("unused")
fun Application.module() {
    install(Koin) {
        slf4jLogger()
        loadMyKoins(environment)
    }

    install(CORS) {
        allowHost("localhost:5173")
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowCredentials = true
        allowNonSimpleContentTypes = true
    }

    configureSerialization()
    configureDatabases()
    configureSecurity()
    configureRouting()
}

fun KoinApplication.loadMyKoins(environment: ApplicationEnvironment): KoinApplication {
    val appModules = module {
        single<ApplicationEnvironment> { environment }
        single<ApplicationProperties> { ApplicationPropertiesImpl(environment) }
        single<MailService> { MailServiceImpl(get()) }
        single<StorageService> { StorageServiceImpl(get()) }
        single<DiaryService> { DiaryServiceImpl(get()) }
        single<PasswordEncoder> { PasswordEncoderImpl() }
        single<UserService> { UserServiceImpl(get(), get(), get(), get(), get<NotificationService>()) }
        single<AccessGroupService> { AccessGroupServiceImpl() }
        single<NotificationService> { NotificationServiceImpl() }

        // Database seeders
        single { ReactionDatabaseSeeder(get(), get()) }

        // Database initializer
        single { DatabaseInitializer(listOf(get<ReactionDatabaseSeeder>())) }

        // Services that depend on seeders
        single<ReactionService> { ReactionServiceImpl(get<StorageService>(), get<AccessGroupService>(), get<NotificationService>(), get<UserService>(), get()) }
        single<PostService> { PostServiceImpl(get<AccessGroupService>(), get<StorageService>(), get<ReactionService>(), get<NotificationService>()) }
        single<DialogService> { DialogServiceImpl(get<UserService>(), get<NotificationService>()) }
    }
    return modules(appModules)
}
