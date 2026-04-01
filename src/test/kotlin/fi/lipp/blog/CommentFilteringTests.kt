package fi.lipp.blog

import fi.lipp.blog.data.CommentDto
import fi.lipp.blog.data.PostDto
import fi.lipp.blog.repository.*
import fi.lipp.blog.service.Viewer
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals

class CommentFilteringTests : UnitTestBase() {

    private fun createPostPostData(
        uri: String = "",
        avatar : String = "avatar url",
        title : String = "sample title",
        text : String = "sample text",
        isPreface : Boolean = false,
        isEncrypted: Boolean = false,
        classes : String = "bold",
        tags : Set<String> = emptySet(),
        readGroup: UUID = groupService.everyoneGroupUUID,
        commentGroup: UUID = groupService.everyoneGroupUUID,
        reactionGroup: UUID = groupService.everyoneGroupUUID,
        commentReactionGroup: UUID = reactionGroup,
    ): PostDto.Create {
        return PostDto.Create(
            uri  = uri,
            avatar  = avatar,
            title  = title,
            text  = text,
            isPreface  = isPreface,
            isEncrypted = isEncrypted,
            classes  = classes,
            tags = tags,
            readGroupId = readGroup,
            commentGroupId = commentGroup,
            reactionGroupId = reactionGroup,
            commentReactionGroupId = commentReactionGroup,
        )
    }

    @Test
    fun `test comments from ignored users are not returned`() {
        transaction {
            // Set up three users: post owner, commenter1, commenter2
            val users = signUsersUp(3)
            val (postOwnerId, postOwnerLogin) = users[0]
            val (commenter1Id, commenter1Login) = users[1]
            val (commenter2Id, commenter2Login) = users[2]

            // Post owner creates a post
            val post = createPostPostData(title = "Test Post")
            postService.addPost(postOwnerId, post)
            val postId = postService.getPost(Viewer.Registered(postOwnerId), postOwnerLogin, "test-post").post.id

            // Commenter1 adds a comment
            val comment1 = CommentDto.Create(postId = postId, avatar = "avatar1", text = "Comment from user 1")
            val addedComment1 = postService.addComment(commenter1Id, comment1)

            // Commenter2 adds a reply to commenter1's comment
            val replyComment = CommentDto.Create(
                postId = postId,
                avatar = "avatar2",
                text = "Reply to user 1's comment",
                parentCommentId = addedComment1.id
            )
            postService.addComment(commenter2Id, replyComment)

            // Commenter2 adds a direct comment
            val comment2 = CommentDto.Create(postId = postId, avatar = "avatar2", text = "Direct comment from user 2")
            postService.addComment(commenter2Id, comment2)

            // Verify all comments are visible initially
            var postView = postService.getPost(Viewer.Registered(postOwnerId), postOwnerLogin, "test-post")
            assertEquals(3, postView.comments.size)

            // Post owner ignores commenter1
            userService.ignoreUser(postOwnerId, commenter1Login)

            commit()

            // Verify that commenter1's comment and commenter2's reply to it are not visible
            postView = postService.getPost(Viewer.Registered(postOwnerId), postOwnerLogin, "test-post")

            // Only commenter2's direct comment should be visible
            assertEquals(1, postView.comments.size)
            assertEquals("Direct comment from user 2", postView.comments[0].text)

            rollback()
        }
    }

    @Test
    fun `test comments reappear after user is unignored`() {
        transaction {
            // Set up three users: post owner, commenter1, commenter2
            val users = signUsersUp(3)
            val (postOwnerId, postOwnerLogin) = users[0]
            val (commenter1Id, commenter1Login) = users[1]
            val (commenter2Id, _) = users[2]

            // Post owner creates a post
            val post = createPostPostData(title = "Test Post")
            postService.addPost(postOwnerId, post)
            val postId = postService.getPost(Viewer.Registered(postOwnerId), postOwnerLogin, "test-post").post.id

            // Commenter1 adds a comment
            val comment1 = CommentDto.Create(postId = postId, avatar = "avatar1", text = "Comment from user 1")
            val addedComment1 = postService.addComment(commenter1Id, comment1)

            // Commenter2 adds a reply to commenter1's comment
            val replyComment = CommentDto.Create(
                postId = postId,
                avatar = "avatar2",
                text = "Reply to user 1's comment",
                parentCommentId = addedComment1.id
            )
            postService.addComment(commenter2Id, replyComment)

            // Post owner ignores commenter1
            userService.ignoreUser(postOwnerId, commenter1Login)

            commit()

            // Verify comments are filtered
            var postView = postService.getPost(Viewer.Registered(postOwnerId), postOwnerLogin, "test-post")
            assertEquals(0, postView.comments.size)

            // Post owner unignores commenter1
            userService.unignoreUser(postOwnerId, commenter1Login)

            commit()

            // Verify all comments are visible again
            postView = postService.getPost(Viewer.Registered(postOwnerId), postOwnerLogin, "test-post")
            assertEquals(2, postView.comments.size)

            rollback()
        }
    }

    @Test
    fun `test comments are hidden when commenter ignores viewer`() {
        transaction {
            // Set up three users: post owner, commenter1, commenter2
            val users = signUsersUp(3)
            val (postOwnerId, postOwnerLogin) = users[0]
            val (commenter1Id, _) = users[1]
            val (viewerId, viewerLogin) = users[2]

            // Post owner creates a post
            val post = createPostPostData(title = "Test Post")
            postService.addPost(postOwnerId, post)
            val postId = postService.getPost(Viewer.Registered(postOwnerId), postOwnerLogin, "test-post").post.id

            // Commenter1 adds a comment
            val comment1 = CommentDto.Create(postId = postId, avatar = "avatar1", text = "Comment from user 1")
            postService.addComment(commenter1Id, comment1)

            // Verify comment is visible to viewer initially
            var postView = postService.getPost(Viewer.Registered(viewerId), postOwnerLogin, "test-post")
            assertEquals(1, postView.comments.size)

            // Commenter1 ignores viewer
            userService.ignoreUser(commenter1Id, viewerLogin)

            commit()

            // Verify comment is not visible to viewer anymore
            postView = postService.getPost(Viewer.Registered(viewerId), postOwnerLogin, "test-post")
            assertEquals(0, postView.comments.size)

            rollback()
        }
    }

    @Test
    fun `test multi-level comment chain filtering when middle user is ignored`() {
        transaction {
            // Set up five users: post owner and four commenters
            val users = signUsersUp(5)
            val (postOwnerId, postOwnerLogin) = users[0]
            val (commenter1Id, commenter1Login) = users[1]
            val (commenter2Id, commenter2Login) = users[2]
            val (commenter3Id, _) = users[3]
            val (commenter4Id, _) = users[4]

            // Post owner creates a post
            val post = createPostPostData(title = "Multi-level Comment Test")
            postService.addPost(postOwnerId, post)
            val postId = postService.getPost(Viewer.Registered(postOwnerId), postOwnerLogin, "multi-level-comment-test").post.id

            // Create a multi-level comment chain: A -> B -> C -> D
            // Commenter1 adds the root comment (A)
            val rootComment = CommentDto.Create(postId = postId, avatar = "avatar1", text = "Root comment (A)")
            val addedRootComment = postService.addComment(commenter1Id, rootComment)

            // Commenter2 adds a reply to the root comment (B)
            val levelBComment = CommentDto.Create(
                postId = postId,
                avatar = "avatar2",
                text = "Level B comment",
                parentCommentId = addedRootComment.id
            )
            val addedLevelBComment = postService.addComment(commenter2Id, levelBComment)

            // Commenter3 adds a reply to the level B comment (C)
            val levelCComment = CommentDto.Create(
                postId = postId,
                avatar = "avatar3",
                text = "Level C comment",
                parentCommentId = addedLevelBComment.id
            )
            val addedLevelCComment = postService.addComment(commenter3Id, levelCComment)

            // Commenter4 adds a reply to the level C comment (D)
            val levelDComment = CommentDto.Create(
                postId = postId,
                avatar = "avatar4",
                text = "Level D comment",
                parentCommentId = addedLevelCComment.id
            )
            postService.addComment(commenter4Id, levelDComment)

            // Commenter1 adds another direct comment (E)
            val directComment = CommentDto.Create(postId = postId, avatar = "avatar1", text = "Direct comment (E)")
            postService.addComment(commenter1Id, directComment)

            // Verify all comments are visible initially
            var postView = postService.getPost(Viewer.Registered(postOwnerId), postOwnerLogin, "multi-level-comment-test")
            assertEquals(5, postView.comments.size)

            // Post owner ignores commenter2 (the middle of the chain)
            userService.ignoreUser(postOwnerId, commenter2Login)

            commit()

            // Verify that comments depending on commenter2 are not visible
            postView = postService.getPost(Viewer.Registered(postOwnerId), postOwnerLogin, "multi-level-comment-test")

            // Only the root comment (A) and direct comment (E) from commenter1 should be visible
            // Comments B, C, and D should be filtered out because they depend on commenter2
            assertEquals(2, postView.comments.size)

            // Verify the visible comments are the expected ones
            val commentTexts = postView.comments.map { it.text }.toSet()
            assertTrue(commentTexts.contains("Root comment (A)"))
            assertTrue(commentTexts.contains("Direct comment (E)"))
            assertFalse(commentTexts.contains("Level B comment"))
            assertFalse(commentTexts.contains("Level C comment"))
            assertFalse(commentTexts.contains("Level D comment"))

            rollback()
        }
    }

    @Test
    fun `test comment reactions from ignored users are not shown`() {
        transaction {
            // Set up four users: post owner, commenter, reactor1, reactor2
            val users = signUsersUp(4)
            val (postOwnerId, postOwnerLogin) = users[0]
            val (commenterId, _) = users[1]
            val (reactor1Id, reactor1Login) = users[2]
            val (reactor2Id, _) = users[3]

            // Post owner creates a post
            val post = createPostPostData(title = "Comment Reactions Test")
            postService.addPost(postOwnerId, post)
            val postId = postService.getPost(Viewer.Registered(postOwnerId), postOwnerLogin, "comment-reactions-test").post.id

            // Commenter adds a comment
            val comment = CommentDto.Create(postId = postId, avatar = "avatar1", text = "Test comment for reactions")
            val addedComment = postService.addComment(commenterId, comment)

            // Create reactions directly in the database
            val heartReactionId = Reactions.select { Reactions.name eq "heart" }.single()[Reactions.id].value
            val fireReactionId = Reactions.select { Reactions.name eq "fire" }.single()[Reactions.id].value

            // Add reactions directly to the database
            CommentReactions.insert {
                it[CommentReactions.user] = EntityID(reactor1Id, Users)
                it[CommentReactions.comment] = EntityID(addedComment.id, Comments)
                it[CommentReactions.reaction] = EntityID(fireReactionId, Reactions)
            }

            CommentReactions.insert {
                it[CommentReactions.user] = EntityID(reactor2Id, Users)
                it[CommentReactions.comment] = EntityID(addedComment.id, Comments)
                it[CommentReactions.reaction] = EntityID(heartReactionId, Reactions)
            }

            commit()

            // Verify both reactions are visible initially
            val initialReactions = CommentReactions
                .select { CommentReactions.comment eq addedComment.id }
                .count()
            assertEquals(2, initialReactions)

            // Post owner ignores reactor1
            userService.ignoreUser(postOwnerId, reactor1Login)

            commit()

            // Verify that reactor1's reaction is filtered out in the query
            val filteredReactions = (CommentReactions innerJoin Users innerJoin Diaries)
                .slice(CommentReactions.comment, CommentReactions.reaction, CommentReactions.user, Diaries.login, Users.nickname)
                .select {
                    val ignoredUsersSubquery = IgnoreList
                        .slice(IgnoreList.ignoredUser)
                        .select { IgnoreList.user eq postOwnerId }

                    val usersWhoIgnoredMeSubquery = IgnoreList
                        .slice(IgnoreList.user)
                        .select { IgnoreList.ignoredUser eq postOwnerId }

                    (CommentReactions.comment eq addedComment.id) and
                    (CommentReactions.user notInSubQuery ignoredUsersSubquery) and
                    (CommentReactions.user notInSubQuery usersWhoIgnoredMeSubquery)
                }
                .count()

            // Only reactor2's reaction should be visible
            assertEquals(1, filteredReactions)

            rollback()
        }
    }
}
