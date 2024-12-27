package fi.lipp.blog

import fi.lipp.blog.mapper.PostMapper
import fi.lipp.blog.mapper.PostMapperImpl
import fi.lipp.blog.plugins.*
import fi.lipp.blog.service.*
import fi.lipp.blog.service.implementations.*
import io.ktor.server.application.*
import org.koin.core.KoinApplication
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

// POOL
// TODO exception handling (Success, Error) wrap
// TODO better avatar storing : 1
// TODO repository level : 1
// TODO more application properties (invite code valid time, time before regeneration codes etc) : 1
// TODO post encryption : 1
// TODO jwt : 2

// LOWEST PRIORITY
// TODO private messages : 5
// TODO todo lists : 8
// TODO post backups & drafts : 1
// TODO comment history (isEdited)
// TODO follow users : 3
fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

@Suppress("unused")
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
        single<ApplicationEnvironment> { environment }
        single<ApplicationProperties> { ApplicationPropertiesImpl(environment) }
        single<MailService> { MailServiceImpl(get()) }
        single<StorageService> { StorageServiceImpl(get()) }
        single<DiaryService> { DiaryServiceImpl(get()) }
        single<PasswordEncoder> { PasswordEncoderImpl() }
        single<UserService> { UserServiceImpl(get(), get(), get()) }
        single<AccessGroupService> { AccessGroupServiceImpl() }
        single<PostMapper> { PostMapperImpl(get()) }
        single<PostService> { PostServiceImpl(get(), get()) }
    }
    return modules(appModules)
}