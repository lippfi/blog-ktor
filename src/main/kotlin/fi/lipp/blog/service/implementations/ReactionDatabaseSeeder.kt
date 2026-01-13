package fi.lipp.blog.service.implementations

import fi.lipp.blog.data.FileUploadData
import fi.lipp.blog.domain.FileEntity
import fi.lipp.blog.domain.ReactionEntity
import fi.lipp.blog.domain.ReactionPackEntity
import fi.lipp.blog.domain.UserEntity
import fi.lipp.blog.repository.ReactionPacks
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
        "100.svg" to "100",
        "check-mark.svg" to "check-mark",
        "clapping.svg" to "clapping",
        "cross-mark.svg" to "cross-mark",
        "drooling.svg" to "drooling",
        "exploding-head.svg" to "exploding-head",
        "eyes.svg" to "eyes",
        "fire.svg" to "fire",
        "fist.svg" to "fist",
        "frowning.svg" to "frowning",
        "fu.svg" to "fu",
        "globe.svg" to "globe",
        "grimacing.svg" to "grimacing",
        "handshake.svg" to "handshake",
        "heart.svg" to "heart",
        "hearts.svg" to "hearts",
        "broken-heart.svg" to "broken-heart",
        "hug.svg" to "hug",
        "kaaba.svg" to "kaaba",
        "kiss.svg" to "kiss",
        "lightning.svg" to "lightning",
        "mouthless.svg" to "mouthless",
        "nerd.svg" to "nerd",
        "neutral.svg" to "neutral",
        "no-entry.svg" to "no-entry",
        "ok.svg" to "ok",
        "palms.svg" to "palms",
        "poo.svg" to "poo",
        "savoring.svg" to "savoring",
        "snowflake.svg" to "snowflake",
        "soon.svg" to "soon",
        "sos.svg" to "sos",
        "sparkles.svg" to "sparkles",
        "sunflower.svg" to "sunflower",
        "sun.svg" to "sun",
        "surprised.svg" to "surprised",
        "suspecting.svg" to "suspecting",
        "thumbs-down.svg" to "thumbs-down",
        "thumbs-up.svg" to "thumbs-up",
        "tongue.svg" to "tongue",
        "upside-down.svg" to "upside-down",
        "writing.svg" to "writing",
    )

    private val flagReactions = mapOf(
        "kazakhstan.svg" to "Kazakhstan",
        "china.svg" to "China",
        "cyprus.svg" to "Cyprus",
        "estonia.svg" to "Estonia",
        "japan.svg" to "Japan",
        "kyrgyzstan.svg" to "Kyrgyzstan",
        "pakistan.svg" to "Pakistan",
        "poland.svg" to "Poland",
        "qatar.svg" to "Qatar",
        "sweden.svg" to "Sweden",
        "turkey.svg" to "Turkey",
        "ukraine.svg" to "Ukraine",
        "russia.svg" to "Russia",
    )

    private val animalReactions = mapOf(
        "baby-chick.svg" to "baby-chick",
        "duck.svg" to "duck",
        "horse-face.svg" to "horse-face",
        "horse.svg" to "horse",
        "monkey-face.svg" to "monkey-face",
        "monkey.svg" to "monkey",
        "octopus.svg" to "octopus",
        "wolf.svg" to "wolf",
    )

    // Smol reactions are stored in resources/img/reactions/smol
    private val smolReactions = mapOf(
        "sticker1.webp" to "suicide",
        "sticker2.webp" to "speech",
        "sticker3.webp" to "chef",
        "sticker4.webp" to "cereal",
        "sticker5.webp" to "fyou",
        "sticker6.webp" to "punch",
        "sticker7.webp" to "peace",
        "sticker8.webp" to "shock",
        "sticker9.webp" to "sad",
        "sticker10.webp" to "high",
        "sticker11.webp" to "broken",
        "sticker12.webp" to "crying",
        "sticker13.webp" to "lick",
        "sticker14.webp" to "milk",
        "sticker15.webp" to "tears",
        "sticker16.webp" to "alien",
        "sticker17.webp" to "krakozyabra",
        "sticker18.webp" to "hearts",
        "sticker19.webp" to "crying2",
        "sticker20.webp" to "annoyed",
        "sticker21.webp" to "tilt",
        "sticker22.webp" to "angry",
        "sticker23.webp" to "suspicious",
        "sticker24.webp" to "surprised",
        "sticker25.webp" to "furious",
        "sticker26.webp" to "nerd",
        "sticker27.webp" to "nerd2",
        "sticker28.webp" to "offended",
        "sticker29.webp" to "love",
        "sticker30.webp" to "happy",
        "sticker31.webp" to "touched",
        "sticker32.webp" to "ola",
        "sticker33.webp" to "silly",
        "sticker34.webp" to "insulted",
        "sticker35.webp" to "shy",
        "sticker36.webp" to "eating",
        "sticker37.webp" to "potato",
        "sticker39.webp" to "hisoka",
        "sticker40.webp" to "runnynose",
        "sticker41.webp" to "crying3",
        "sticker42.webp" to "heart",
        "sticker43.webp" to "love2",
        "sticker44.webp" to "flowers",
        "sticker45.webp" to "serious",
        "sticker46.webp" to "wink",
        "sticker47.webp" to "hehe",
        "sticker48.webp" to "heheq",
        "sticker49.webp" to "nothehe",
        "sticker50.webp" to "talk",
        "sticker51.webp" to "listening",
        "sticker52.webp" to "night",
        "sticker53.webp" to "clown",
        "sticker54.webp" to "thumbsuppleased",
        "sticker55.webp" to "thumbsupsad",
        "sticker56.webp" to "thumbsup",
        "sticker57.webp" to "horror",
        "sticker58.webp" to "pleased",
        "sticker59.webp" to "hearttears",
        "sticker60.webp" to "silent",
        "sticker61.webp" to "singing",
        "sticker62.webp" to "handshake",
        "sticker63.webp" to "smol",
        "sticker88.webp" to "friends",
        "sticker91.webp" to "angel",
        "sticker92.webp" to "demon",
    )

    /**
     * Seeds the database with initial reaction data.
     * This method creates all basic and smol reactions if they don't already exist.
     */
    override fun seed() {
        val systemUserId = userService.getOrCreateSystemUser()
        
        transaction {
            seedReactions(systemUserId, "basic", basicReactions, "img/reactions/basic")
            seedReactions(systemUserId, "animals", animalReactions, "img/reactions/animals")
            seedReactions(systemUserId, "flags", flagReactions, "img/reactions/flags")
            seedReactions(systemUserId, "smol", smolReactions, "img/reactions/smol", "smol-alien")
            
            commit()
        }
    }
    
    /**
     * Seeds a set of reactions from a specific resource path.
     * 
     * @param systemUserId The ID of the system user who will be the creator of the reactions
     * @param packName The name of the reaction pack
     * @param reactions A map of filename to reaction name
     * @param resourcePathPrefix The prefix path where the reaction images are stored
     * @param packIconReactionName The name of the reaction whose icon will be used as pack icon
     */
    private fun seedReactions(
        systemUserId: UUID, 
        packName: String, 
        reactions: Map<String, String>, 
        resourcePathPrefix: String,
        packIconReactionName: String? = null
    ) {
        val pack = ReactionPackEntity.find { ReactionPacks.name eq packName }.firstOrNull() 
            ?: ReactionPackEntity.new { 
                this.name = packName
                this.creator = UserEntity.findById(systemUserId)!!
            }

        reactions.toList().forEachIndexed { index, (fileName, reactionName) ->
            val existingReaction = ReactionEntity.find { Reactions.name eq reactionName }.firstOrNull()
            
            if (existingReaction != null) {
                if (existingReaction.ordinal != index) {
                    existingReaction.ordinal = index
                }
                if (reactionName == packIconReactionName) {
                    pack.icon = existingReaction.icon
                }
            } else {
                val resourcePath = "$resourcePathPrefix/$fileName"
                val inputStream = this::class.java.classLoader.getResourceAsStream(resourcePath)
                    ?: throw IllegalStateException("Resource not found: $resourcePath")

                val fileUploadData = FileUploadData(
                    fullName = fileName,
                    inputStream = inputStream
                )

                val storedFile = storageService.storeReaction(systemUserId, fileName, fileUploadData)
                val iconFile = FileEntity.findById(storedFile.id) ?: throw FileNotFoundException()

                ReactionEntity.new {
                    this.name = reactionName
                    this.icon = iconFile
                    this.pack = pack
                    this.creator = EntityID(systemUserId, Users)
                    this.ordinal = index
                }

                if (reactionName == packIconReactionName) {
                    pack.icon = iconFile
                }
            }
        }
    }
    
}