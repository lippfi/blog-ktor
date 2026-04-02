package fi.lipp.blog

import fi.lipp.blog.data.FileUploadData
import fi.lipp.blog.data.UserPermission
import fi.lipp.blog.model.exceptions.ReactionNameIsTakenException
import fi.lipp.blog.model.exceptions.ReactionNotFoundException
import fi.lipp.blog.model.exceptions.ReactionPackNotFoundException
import fi.lipp.blog.model.exceptions.WrongUserException
import fi.lipp.blog.service.Viewer
import org.junit.Before
import org.junit.Test
import java.util.*
import kotlin.test.*

class ReactionManagementTests : UnitTestBase() {
    private lateinit var user1Id: UUID
    private lateinit var user2Id: UUID

    @Before
    fun setUp() {
        val ids = signUsersUp()
        user1Id = ids.first
        user2Id = ids.second
    }

    private fun createIcon(): FileUploadData = FileUploadData("icon.png", avatarFile1.readBytes())

    // =====================================================================
    // isReactionNameBusy
    // =====================================================================

    @Test
    fun `isReactionNameBusy returns false for unused name`() {
        assertFalse(reactionService.isReactionNameBusy("nonexistent-name"))
    }

    @Test
    fun `isReactionNameBusy returns true for existing reaction name`() {
        reactionService.createReaction(Viewer.Registered(user1Id), "taken-name", "custom", createIcon())
        assertTrue(reactionService.isReactionNameBusy("taken-name"))
    }

    @Test
    fun `isReactionNameBusy returns false after reaction is deleted`() {
        reactionService.createReaction(Viewer.Registered(user1Id), "temp-name", "custom", createIcon())
        assertTrue(reactionService.isReactionNameBusy("temp-name"))
        reactionService.deleteReaction(Viewer.Registered(user1Id), "temp-name")
        assertFalse(reactionService.isReactionNameBusy("temp-name"))
    }

    @Test
    fun `isReactionNameBusy detects seeded reaction names`() {
        // "fire" is a seeded reaction from ReactionDatabaseSeeder
        assertTrue(reactionService.isReactionNameBusy("fire"))
    }

    // =====================================================================
    // renameReaction
    // =====================================================================

    @Test
    fun `renameReaction successfully renames a reaction`() {
        reactionService.createReaction(Viewer.Registered(user1Id), "old-name", "custom", createIcon())
        reactionService.renameReaction(Viewer.Registered(user1Id), "old-name", "new-name")

        assertFalse(reactionService.isReactionNameBusy("old-name"))
        assertTrue(reactionService.isReactionNameBusy("new-name"))
    }

    @Test
    fun `renameReaction fails for non-creator`() {
        reactionService.createReaction(Viewer.Registered(user1Id), "my-reaction", "custom", createIcon())
        assertFailsWith<WrongUserException> {
            reactionService.renameReaction(Viewer.Registered(user2Id), "my-reaction", "stolen-name")
        }
        // Verify original name still exists
        assertTrue(reactionService.isReactionNameBusy("my-reaction"))
    }

    @Test
    fun `renameReaction fails for non-existent reaction`() {
        assertFailsWith<ReactionNotFoundException> {
            reactionService.renameReaction(Viewer.Registered(user1Id), "no-such-reaction", "new-name")
        }
    }

    @Test
    fun `renameReaction fails when new name is already taken`() {
        reactionService.createReaction(Viewer.Registered(user1Id), "reaction-a", "custom", createIcon())
        reactionService.createReaction(Viewer.Registered(user1Id), "reaction-b", "custom", createIcon())
        assertFailsWith<ReactionNameIsTakenException> {
            reactionService.renameReaction(Viewer.Registered(user1Id), "reaction-a", "reaction-b")
        }
        // Verify both reactions still exist with their original names
        assertTrue(reactionService.isReactionNameBusy("reaction-a"))
        assertTrue(reactionService.isReactionNameBusy("reaction-b"))
    }

    @Test
    fun `renameReaction validates new name format`() {
        reactionService.createReaction(Viewer.Registered(user1Id), "valid-name", "custom", createIcon())
        assertFailsWith<IllegalArgumentException> {
            reactionService.renameReaction(Viewer.Registered(user1Id), "valid-name", "invalid name!")
        }
        // Verify reaction still has original name
        assertTrue(reactionService.isReactionNameBusy("valid-name"))
    }

    @Test
    fun `renameReaction with invalid name starting with number`() {
        reactionService.createReaction(Viewer.Registered(user1Id), "good-name", "custom", createIcon())
        assertFailsWith<IllegalArgumentException> {
            reactionService.renameReaction(Viewer.Registered(user1Id), "good-name", "123invalid")
        }
    }

    // =====================================================================
    // updateReactionPack
    // =====================================================================

    @Test
    fun `updateReactionPack renames pack successfully`() {
        reactionService.createReaction(Viewer.Registered(user1Id), "pack-reaction", "my-pack", createIcon())
        val updated = reactionService.updateReactionPack(Viewer.Registered(user1Id), "my-pack", "renamed-pack", null)
        assertEquals("renamed-pack", updated.name)
        assertEquals(1, updated.reactions.size)
        assertEquals("pack-reaction", updated.reactions[0].name)
    }

    @Test
    fun `updateReactionPack changes icon successfully`() {
        reactionService.createReaction(Viewer.Registered(user1Id), "icon-reaction", "icon-pack", createIcon())
        val newIcon = FileUploadData("new-icon.png", avatarFile2.readBytes())
        val updated = reactionService.updateReactionPack(Viewer.Registered(user1Id), "icon-pack", null, newIcon)
        assertEquals("icon-pack", updated.name)
        assertTrue(updated.iconUri.isNotEmpty())
    }

    @Test
    fun `updateReactionPack changes both name and icon`() {
        reactionService.createReaction(Viewer.Registered(user1Id), "both-reaction", "both-pack", createIcon())
        val newIcon = FileUploadData("new-icon.png", avatarFile2.readBytes())
        val updated = reactionService.updateReactionPack(Viewer.Registered(user1Id), "both-pack", "new-both-pack", newIcon)
        assertEquals("new-both-pack", updated.name)
        assertTrue(updated.iconUri.isNotEmpty())
    }

    @Test
    fun `updateReactionPack fails for non-creator`() {
        reactionService.createReaction(Viewer.Registered(user1Id), "owned-reaction", "owned-pack", createIcon())
        assertFailsWith<WrongUserException> {
            reactionService.updateReactionPack(Viewer.Registered(user2Id), "owned-pack", "hacked-pack", null)
        }
    }

    @Test
    fun `updateReactionPack fails for non-existent pack`() {
        assertFailsWith<ReactionPackNotFoundException> {
            reactionService.updateReactionPack(Viewer.Registered(user1Id), "no-such-pack", "new-name", null)
        }
    }

    @Test
    fun `updateReactionPack fails when new name is already taken`() {
        reactionService.createReaction(Viewer.Registered(user1Id), "r-in-pack-a", "pack-a", createIcon())
        reactionService.createReaction(Viewer.Registered(user1Id), "r-in-pack-b", "pack-b", createIcon())
        assertFailsWith<ReactionNameIsTakenException> {
            reactionService.updateReactionPack(Viewer.Registered(user1Id), "pack-a", "pack-b", null)
        }
    }

    @Test
    fun `updateReactionPack with same name as current does not throw`() {
        reactionService.createReaction(Viewer.Registered(user1Id), "same-r", "same-pack", createIcon())
        val updated = reactionService.updateReactionPack(Viewer.Registered(user1Id), "same-pack", "same-pack", null)
        assertEquals("same-pack", updated.name)
    }

    @Test
    fun `updateReactionPack with null name and null icon returns pack unchanged`() {
        reactionService.createReaction(Viewer.Registered(user1Id), "noop-r", "noop-pack", createIcon())
        val updated = reactionService.updateReactionPack(Viewer.Registered(user1Id), "noop-pack", null, null)
        assertEquals("noop-pack", updated.name)
        assertEquals(1, updated.reactions.size)
    }

    // =====================================================================
    // createReaction - creator permission checks
    // =====================================================================

    @Test
    fun `createReaction succeeds when user creates a new pack (becomes creator)`() {
        val result = reactionService.createReaction(Viewer.Registered(user1Id), "new-r", "new-pack", createIcon())
        assertNotNull(result)
        assertEquals("new-r", result.name)
    }

    @Test
    fun `createReaction succeeds for pack creator adding second reaction`() {
        reactionService.createReaction(Viewer.Registered(user1Id), "first-r", "creator-pack", createIcon())
        val second = reactionService.createReaction(Viewer.Registered(user1Id), "second-r", "creator-pack", createIcon())
        assertNotNull(second)
        assertEquals("second-r", second.name)
    }

    @Test
    fun `createReaction fails for non-creator of existing pack`() {
        // user1 creates the pack
        reactionService.createReaction(Viewer.Registered(user1Id), "owner-r", "locked-pack", createIcon())
        // user2 tries to add to user1's pack
        assertFailsWith<WrongUserException> {
            reactionService.createReaction(Viewer.Registered(user2Id), "intruder-r", "locked-pack", createIcon())
        }
    }

    @Test
    fun `createReaction to basic pack fails for user without WRITE_BASIC_REACTIONS permission`() {
        // "basic" pack is created by system user during seeding
        val viewer = Viewer.Registered(user1Id) // no special permissions
        assertFailsWith<WrongUserException> {
            reactionService.createReaction(viewer, "user-basic-r", "basic", createIcon())
        }
    }

    @Test
    fun `createReaction to basic pack succeeds for user with WRITE_BASIC_REACTIONS permission`() {
        val viewer = Viewer.Registered(user1Id, setOf(UserPermission.WRITE_BASIC_REACTIONS))
        val result = reactionService.createReaction(viewer, "permitted-basic-r", "basic", createIcon())
        assertNotNull(result)
        assertEquals("permitted-basic-r", result.name)
    }

    @Test
    fun `WRITE_BASIC_REACTIONS permission does not grant access to non-basic packs`() {
        // user1 creates a non-basic pack
        reactionService.createReaction(Viewer.Registered(user1Id), "my-r", "non-basic-pack", createIcon())
        // user2 with WRITE_BASIC_REACTIONS tries to add to non-basic pack
        val viewer = Viewer.Registered(user2Id, setOf(UserPermission.WRITE_BASIC_REACTIONS))
        assertFailsWith<WrongUserException> {
            reactionService.createReaction(viewer, "sneaky-r", "non-basic-pack", createIcon())
        }
    }

    @Test
    fun `system user can add reactions to basic pack as creator`() {
        val systemUserId = userService.getOrCreateSystemUser()
        // System user is the creator of "basic" pack from seeding, so this should work
        val viewer = Viewer.Registered(systemUserId)
        val result = reactionService.createReaction(viewer, "system-basic-r", "basic", createIcon())
        assertNotNull(result)
    }

    // =====================================================================
    // deleteReaction - WRITE_BASIC_REACTIONS permission checks
    // =====================================================================

    @Test
    fun `deleteReaction in basic pack succeeds for user with WRITE_BASIC_REACTIONS permission`() {
        val viewer = Viewer.Registered(user1Id, setOf(UserPermission.WRITE_BASIC_REACTIONS))
        // "fire" is a seeded reaction in "basic" pack
        reactionService.deleteReaction(viewer, "fire")
        assertFalse(reactionService.isReactionNameBusy("fire"))
    }

    @Test
    fun `deleteReaction in basic pack fails for user without WRITE_BASIC_REACTIONS permission`() {
        val viewer = Viewer.Registered(user1Id) // no special permissions
        assertFailsWith<WrongUserException> {
            reactionService.deleteReaction(viewer, "fire")
        }
        assertTrue(reactionService.isReactionNameBusy("fire"))
    }

    @Test
    fun `WRITE_BASIC_REACTIONS does not grant delete access to non-basic pack reactions`() {
        reactionService.createReaction(Viewer.Registered(user1Id), "custom-r", "custom-pack", createIcon())
        val viewer = Viewer.Registered(user2Id, setOf(UserPermission.WRITE_BASIC_REACTIONS))
        assertFailsWith<WrongUserException> {
            reactionService.deleteReaction(viewer, "custom-r")
        }
        assertTrue(reactionService.isReactionNameBusy("custom-r"))
    }

    // =====================================================================
    // renameReaction - WRITE_BASIC_REACTIONS permission checks
    // =====================================================================

    @Test
    fun `renameReaction in basic pack succeeds for user with WRITE_BASIC_REACTIONS permission`() {
        val viewer = Viewer.Registered(user1Id, setOf(UserPermission.WRITE_BASIC_REACTIONS))
        reactionService.renameReaction(viewer, "fire", "flame")
        assertFalse(reactionService.isReactionNameBusy("fire"))
        assertTrue(reactionService.isReactionNameBusy("flame"))
    }

    @Test
    fun `renameReaction in basic pack fails for user without WRITE_BASIC_REACTIONS permission`() {
        val viewer = Viewer.Registered(user1Id) // no special permissions
        assertFailsWith<WrongUserException> {
            reactionService.renameReaction(viewer, "fire", "flame")
        }
        assertTrue(reactionService.isReactionNameBusy("fire"))
        assertFalse(reactionService.isReactionNameBusy("flame"))
    }

    @Test
    fun `WRITE_BASIC_REACTIONS does not grant rename access to non-basic pack reactions`() {
        reactionService.createReaction(Viewer.Registered(user1Id), "custom-rename-r", "custom-rename-pack", createIcon())
        val viewer = Viewer.Registered(user2Id, setOf(UserPermission.WRITE_BASIC_REACTIONS))
        assertFailsWith<WrongUserException> {
            reactionService.renameReaction(viewer, "custom-rename-r", "stolen-name")
        }
        assertTrue(reactionService.isReactionNameBusy("custom-rename-r"))
    }

    // =====================================================================
    // updateReactionPack - WRITE_BASIC_REACTIONS permission checks
    // =====================================================================

    @Test
    fun `updateReactionPack for basic pack succeeds for user with WRITE_BASIC_REACTIONS permission`() {
        val viewer = Viewer.Registered(user1Id, setOf(UserPermission.WRITE_BASIC_REACTIONS))
        val newIcon = FileUploadData("new-icon.png", avatarFile2.readBytes())
        val updated = reactionService.updateReactionPack(viewer, "basic", null, newIcon)
        assertEquals("basic", updated.name)
        assertTrue(updated.iconUri.isNotEmpty())
    }

    @Test
    fun `updateReactionPack for basic pack fails for user without WRITE_BASIC_REACTIONS permission`() {
        val viewer = Viewer.Registered(user1Id) // no special permissions
        assertFailsWith<WrongUserException> {
            reactionService.updateReactionPack(viewer, "basic", null, FileUploadData("icon.png", avatarFile2.readBytes()))
        }
    }

    @Test
    fun `WRITE_BASIC_REACTIONS does not grant updateReactionPack access to non-basic packs`() {
        reactionService.createReaction(Viewer.Registered(user1Id), "pack-r", "protected-pack", createIcon())
        val viewer = Viewer.Registered(user2Id, setOf(UserPermission.WRITE_BASIC_REACTIONS))
        assertFailsWith<WrongUserException> {
            reactionService.updateReactionPack(viewer, "protected-pack", "hacked-name", null)
        }
    }
}
