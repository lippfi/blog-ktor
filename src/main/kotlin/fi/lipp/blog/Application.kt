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

// POOL
// Cascade delete and stuff
// TODO API documentation
// TODO Integration tests
// TODO Cors

// SECOND ITERATION
// Better reactions
// Device sessions && token invalidation
// Notifications
// Avatar validation
// Custom website design css
// File size limits
// Create dirs when store file

// Multiple diary styles (switch between them)
// Friends
// Communities
// Private messages
// Caches

// THIRD ITERATION
// background music
// Ignore list
// TODO diary backup & diary restore
// TODO post backups & drafts : 1
// TODO follow users : 3

// FORTH ITERATION
// TODO telegram integration

// LOWEST PRIORITY
// Multiple languages
// TODO moderation
// TODO banned users
// TODO todo lists : 8
// TODO what about Pages? Are they ready? Should I have start page or some order on them? Should pages have visibility? Own links?
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
        single<UserService> { UserServiceImpl(get(), get(), get(), get()) }
        single<AccessGroupService> { AccessGroupServiceImpl() }
        single<PostService> { PostServiceImpl(get(), get()) }
    }
    return modules(appModules)
}
