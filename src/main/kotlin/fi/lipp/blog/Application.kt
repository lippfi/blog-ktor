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

// My goals
// TODO figure out a better way to store reactions
// TODO what about Pages? Are they ready? Should I have start page or some order on them? Should pages have visibility? Own links?
// Profile page
// TODO Cors
// Restrict reactions to posts

// MVP
// Allow post owners to delete comments of other users
// Settings
// Communities
// Ignore list
// Custom website design css
// Multiple diary styles (switch between them)
// Device sessions && token invalidation
// Online and activity

// POOL
// TODO telegram integration
// Cascade delete and stuff
// TODO API documentation
// TODO Integration tests

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
        allowHost("localhost:5173", schemes = listOf("http"))
        allowHost("lipp.fi", schemes = listOf("https"))

        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)

        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.Accept)

        allowCredentials = true
        allowNonSimpleContentTypes = true
    }

    configureSerialization()
    configureDatabases()
    configureSecurity()
    configureWebSockets()
    configureRouting()
}

fun KoinApplication.loadMyKoins(environment: ApplicationEnvironment): KoinApplication {
    val appModules = module {
        single<ApplicationEnvironment> { environment }
        single<ApplicationProperties> { ApplicationPropertiesImpl(environment) }
        single<MailService> { MailServiceImpl(get()) }
        single<StorageService> {
            val props = get<ApplicationProperties>()
            if (props.useCdn) CdnStorageServiceImpl(props) else LocalStorageServiceImpl(props)
        }
        single<PasswordEncoder> { PasswordEncoderImpl(get<ApplicationProperties>().bcryptCost) }
        single<NotificationWebSocketService> { NotificationWebSocketServiceImpl() }
        single<NotificationService> { NotificationServiceImpl(get()) }
        single<CommentWebSocketService> { CommentWebSocketServiceImpl(get()) }
        single<AccessGroupService> { AccessGroupServiceImpl() }
        single<SessionService> { SessionServiceImpl() }
        single<GeoLocationService> { GeoLocationServiceImpl(environment.config.property("geoip.database").getString()) }
        single<UserService> { UserServiceImpl(get(), get(), get(), get(), get<NotificationService>(), get(), get(), lazy { get<ReactionService>() }) }
        single<DiaryService> { DiaryServiceImpl(get(), get()) }

        // Database seeders
        single { ReactionDatabaseSeeder(get(), get()) }

        // Database initializer
        single { DatabaseInitializer(listOf(get<ReactionDatabaseSeeder>())) }

        // Services that depend on seeders
        single<ReactionService> { ReactionServiceImpl(get<StorageService>(), get<AccessGroupService>(), get<NotificationService>(), get(), get<CommentWebSocketService>()) }
        single<PostService> { PostServiceImpl(get<AccessGroupService>(), get<StorageService>(), get<ReactionService>(), get<NotificationService>(), get<CommentWebSocketService>()) }
        single<DialogService> { DialogServiceImpl(get<UserService>(), get<NotificationService>()) }
    }
    return modules(appModules)
}
