package fi.lipp.blog.util

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object GenericArrayListSerializer : KSerializer<ArrayList<Any>> {
    private val listSerializer = ListSerializer(PolymorphicSerializer(Any::class))

    override val descriptor: SerialDescriptor = listSerializer.descriptor

    override fun serialize(encoder: Encoder, value: ArrayList<Any>) {
        listSerializer.serialize(encoder, value)
    }

    override fun deserialize(decoder: Decoder): ArrayList<Any> {
        return ArrayList(listSerializer.deserialize(decoder))
    }
}

