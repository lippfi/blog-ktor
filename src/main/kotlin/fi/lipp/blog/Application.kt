package fi.lipp.blog

import fi.lipp.blog.plugins.*
import fi.lipp.blog.service.*
import fi.lipp.blog.service.implementations.*
import io.ktor.server.application.*
import org.koin.core.KoinApplication
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

// POOL
// TODO avoid Diary and Diary entity & give up on communities OR User is a global user for other services that may have a nullable diary
// TODO better avatar storing : 1
// TODO repository level : 1
// TODO more application properties (invite code valid time, time before regeneration codes etc) : 1
// TODO follow users : 3
// TODO post encryption : 1
// TODO delete user & cascade delete : 2
// TODO jwt : 2

// LOWEST PRIORITY
// TODO private messages : 5
// TODO todo lists : 8
// TODO post backups & drafts : 1
// TODO comment history (isEdited)
fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    install(Koin) {
        slf4jLogger()
        loadMyKoins(environment)
    }

    configureSerialization()
    configureDatabases()
    configureSecurity()
    configureRouting()
}

fun KoinApplication.loadMyKoins(environment: ApplicationEnvironment): KoinApplication {
    val appModules = module {
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