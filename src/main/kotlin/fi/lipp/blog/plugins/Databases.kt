package fi.lipp.blog.plugins

import fi.lipp.blog.domain.Notifications
import fi.lipp.blog.repository.*
import fi.lipp.blog.service.implementations.DatabaseInitializer
import io.ktor.server.application.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.core.context.GlobalContext.get

fun Application.configureDatabases() {
    Database.connect(
        url = "jdbc:h2:file:./data/blog;DB_CLOSE_DELAY=-1",
        user = "root",
        driver = "org.h2.Driver",
        password = ""
    )
    transaction {
        SchemaUtils.create(
            Users,
            Files,
            UserAvatars,
            Tags,
            Diaries,
            DiaryStyles,
            DiaryStyleJunctions,
            AccessGroups,
            CustomGroupUsers,
            InviteCodes,
            PasswordResets,
            PendingRegistrations,
            PendingEmailChanges,
            Posts,
            PostTags,
            Comments,
            Reactions,
            PostReactions,
            AnonymousPostReactions,
            UserUploads,
            FriendRequests,
            Friends,
            FriendLabels,
            Notifications,
            Dialogs,
            Messages,
            HiddenDialogs,
            UserFollows,
            CommentReactions,
            AnonymousCommentReactions,
            NotificationSettings,
            PostSubscriptions,
        )
    }

    // Initialize database with seeders
    val databaseInitializer = get().get<DatabaseInitializer>()
    databaseInitializer.initialize()
//    val userService = UserService(database)
//    routing {
//        // Create user
//        post("/users") {
//            val user = call.receive<ExposedUser>()
//            val id = userService.create(user)
//            call.respond(HttpStatusCode.Created, id)
//        }
//
//            // Read user
//        get("/users/{id}") {
//            val id = call.parameters["id"]?.toInt() ?: throw IllegalArgumentException("Invalid ID")
//            val user = userService.read(id)
//            if (user != null) {
//                call.respond(HttpStatusCode.OK, user)
//            } else {
//                call.respond(HttpStatusCode.NotFound)
//            }
//        }
//
//            // Update user
//        put("/users/{id}") {
//            val id = call.parameters["id"]?.toInt() ?: throw IllegalArgumentException("Invalid ID")
//            val user = call.receive<ExposedUser>()
//            userService.update(id, user)
//            call.respond(HttpStatusCode.OK)
//        }
//
//            // Delete user
//        delete("/users/{id}") {
//            val id = call.parameters["id"]?.toInt() ?: throw IllegalArgumentException("Invalid ID")
//            userService.delete(id)
//            call.respond(HttpStatusCode.OK)
//        }
//    }
}
