package fi.lipp.blog

import fi.lipp.blog.data.UserDto
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.*

class SearchUserTests : UnitTestBase() {
    
    @Test
    fun `search users by nickname`() {
        transaction {
            // Sign up two test users
            val (userId1, userId2) = signUsersUp()
            
            // Search for users by nickname
            val results = userService.search("dog_lover")
            
            // Verify results
            assertEquals(1, results.size)
            assertEquals(testUser.login, results[0].login)
            assertEquals(testUser.nickname, results[0].nickname)
            
            rollback()
        }
    }
    
    @Test
    fun `search users by login`() {
        transaction {
            // Sign up two test users
            val (userId1, userId2) = signUsersUp()
            
            // Search for users by login
            val results = userService.search("baby")
            
            // Verify results
            assertEquals(1, results.size)
            assertEquals(testUser2.login, results[0].login)
            assertEquals(testUser2.nickname, results[0].nickname)
            
            rollback()
        }
    }
    
    @Test
    fun `search returns empty list when no matches`() {
        transaction {
            // Sign up two test users
            val (userId1, userId2) = signUsersUp()
            
            // Search for users with a term that doesn't match any user
            val results = userService.search("nonexistent")
            
            // Verify results
            assertTrue(results.isEmpty())
            
            rollback()
        }
    }
    
    @Test
    fun `search results are limited to 10 users`() {
        transaction {
            // Sign up 15 users with similar names
            val users = signUsersUp(15)
            
            // Search with a term that would match all users
            val results = userService.search("@mail.com")
            
            // Verify results are limited to 10
            assertTrue(results.size <= 10)
            
            rollback()
        }
    }
    
    @Test
    fun `search results are sorted alphabetically by login`() {
        transaction {
            // Sign up two test users
            val (userId1, userId2) = signUsersUp()
            
            // Search with a term that would match both users
            val results = userService.search("lover")
            
            // Verify results are sorted alphabetically by login
            if (results.size > 1) {
                for (i in 0 until results.size - 1) {
                    assertTrue(results[i].login <= results[i + 1].login)
                }
            }
            
            rollback()
        }
    }
}