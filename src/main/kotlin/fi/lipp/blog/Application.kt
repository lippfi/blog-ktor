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
// TODO setup Koin in test
// TODO Cors

// SECOND ITERATION
// Custom website design css
// Better file storage (in login-named folder)
// Multi-step registration (basic - language, age, diary title)
// Better reactions
// Multiple languages
// Friends
// Communities
// Private messages
// Notifications
// Device sessions && token invalidation

// THIRD ITERATION
// background music
// Ignore list
// TODO diary backup & diary restore
// TODO post backups & drafts : 1
// TODO follow users : 3

// LOWEST PRIORITY
// TODO banned users
// TODO better avatar storing : 1
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
        single<UserService> { UserServiceImpl(get(), get(), get()) }
        single<AccessGroupService> { AccessGroupServiceImpl() }
        single<PostService> { PostServiceImpl(get()) }
    }
    return modules(appModules)
}