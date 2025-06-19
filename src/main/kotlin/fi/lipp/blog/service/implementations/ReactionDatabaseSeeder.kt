package fi.lipp.blog.service.implementations

import fi.lipp.blog.data.FileUploadData
import fi.lipp.blog.domain.FileEntity
import fi.lipp.blog.domain.ReactionEntity
import fi.lipp.blog.repository.Reactions
import fi.lipp.blog.repository.Users
import fi.lipp.blog.service.DatabaseSeeder
import fi.lipp.blog.service.StorageService
import fi.lipp.blog.service.UserService
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.FileNotFoundException
import java.util.*

/**
 * Implementation of DatabaseSeeder for reactions.
 * This class is responsible for seeding the database with initial reaction data.
 */
class ReactionDatabaseSeeder(
    private val storageService: StorageService,
    private val userService: UserService
) : DatabaseSeeder {

    // Basic reactions are stored in resources/img/reactions/basic
    private val basicReactions = mapOf(
        "heart.svg" to "heart",
        "fire.svg" to "fire"
    )

    // Smol reactions are stored in resources/img/reactions/smol
    private val smolReactions = mapOf(
        "sticker1.webp" to "smol-suicide",
        "sticker2.webp" to "smol-speech",
        "sticker3.webp" to "smol-shef",
        "sticker4.webp" to "smol-cereal",
        "sticker5.webp" to "smol-fyou",
        "sticker6.webp" to "smol-punch",
        "sticker7.webp" to "smol-peace",
        "sticker8.webp" to "smol-shock",
        "sticker9.webp" to "smol-sad",
        "sticker10.webp" to "smol-high",
        "sticker11.webp" to "smol-broken",
        "sticker12.webp" to "smol-crying",
        "sticker13.webp" to "smol-lick",
        "sticker14.webp" to "smol-milk",
        "sticker15.webp" to "smol-tears",
        "sticker16.webp" to "smol-alien",
        "sticker17.webp" to "smol-krakozyabra",
        "sticker18.webp" to "smol-hearts",
        "sticker19.webp" to "smol-crying2",
        "sticker20.webp" to "smol-annoyed",
        "sticker21.webp" to "smol-tilt",
        "sticker22.webp" to "smol-angry",
        "sticker23.webp" to "smol-suspicious",
        "sticker24.webp" to "smol-surprized",
        "sticker25.webp" to "smol-furious",
        "sticker26.webp" to "smol-nerd",
        "sticker27.webp" to "smol-nerd2",
        "sticker28.webp" to "smol-offended",
        "sticker29.webp" to "smol-love",
        "sticker30.webp" to "smol-happy",
        "sticker31.webp" to "smol-touched",
        "sticker32.webp" to "smol-ola",
        "sticker33.webp" to "smol-silly",
        "sticker34.webp" to "smol-insulted",
        "sticker35.webp" to "smol-shy",
        "sticker36.webp" to "smol-eating",
        "sticker37.webp" to "smol-potato",
        "sticker39.webp" to "smol-hisoka",
        "sticker40.webp" to "smol-runnynose",
        "sticker41.webp" to "smol-crying3",
        "sticker42.webp" to "smol-heart",
        "sticker43.webp" to "smol-love2",
        "sticker44.webp" to "smol-flowers",
        "sticker45.webp" to "smol-serious",
        "sticker46.webp" to "smol-wink",
        "sticker47.webp" to "smol-hehe",
        "sticker48.webp" to "smol-heheq",
        "sticker49.webp" to "smol-nothehe",
        "sticker50.webp" to "smol-talk",
        "sticker51.webp" to "smol-listening",
        "sticker52.webp" to "smol-night",
        "sticker53.webp" to "smol-clown",
        "sticker54.webp" to "smol-thumbsuppleased",
        "sticker55.webp" to "smol-thumbsupsad",
        "sticker56.webp" to "smol-thumbsup",
        "sticker57.webp" to "smol-horror",
        "sticker58.webp" to "smol-pleased",
        "sticker59.webp" to "smol-hearttears",
        "sticker60.webp" to "smol-silent",
        "sticker61.webp" to "smol-singing",
        "sticker62.webp" to "smol-handshake",
        "sticker63.webp" to "smol-smol",
        "sticker88.webp" to "smol-friends",
        "sticker91.webp" to "smol-angel",
        "sticker92.webp" to "smol-demon",
    )

    /**
     * Seeds the database with initial reaction data.
     * This method creates all basic and smol reactions if they don't already exist.
     */
    override fun seed() {
        val systemUserId = userService.getOrCreateSystemUser()
        
        transaction {
            // Seed basic reactions
            seedReactions(systemUserId, basicReactions, "img/reactions/basic")
            
            // Seed smol reactions
            seedReactions(systemUserId, smolReactions, "img/reactions/smol")
            
            commit()
        }
    }
    
    /**
     * Seeds a set of reactions from a specific resource path.
     * 
     * @param systemUserId The ID of the system user who will be the creator of the reactions
     * @param reactions A map of filename to reaction name
     * @param resourcePathPrefix The prefix path where the reaction images are stored
     */
    private fun seedReactions(systemUserId: UUID, reactions: Map<String, String>, resourcePathPrefix: String) {
        reactions.forEach { (fileName, reactionName) ->
            if (!isReactionNameUsed(reactionName)) {
                val resourcePath = "$resourcePathPrefix/$fileName"
                val inputStream = this::class.java.classLoader.getResourceAsStream(resourcePath)
                    ?: throw IllegalStateException("Resource not found: $resourcePath")

                val fileUploadData = FileUploadData(
                    fullName = fileName,
                    inputStream = inputStream
                )

                val storedFile = storageService.storeReaction(systemUserId, fileName,fileUploadData)
                val iconFile = FileEntity.findById(storedFile.id) ?: throw FileNotFoundException()

                ReactionEntity.new {
                    this.name = reactionName
                    this.icon = iconFile
                    this.creator = EntityID(systemUserId, Users)
                }
            }
        }
    }
    
    /**
     * Checks if a reaction with the given name already exists in the database.
     * 
     * @param name The name of the reaction to check
     * @return true if a reaction with the given name exists, false otherwise
     */
    private fun isReactionNameUsed(name: String): Boolean {
        return ReactionEntity.find { Reactions.name eq name }.firstOrNull() != null
    }
}