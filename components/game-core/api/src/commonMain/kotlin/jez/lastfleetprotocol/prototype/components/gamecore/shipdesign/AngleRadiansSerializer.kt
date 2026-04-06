package jez.lastfleetprotocol.prototype.components.gamecore.shipdesign

import com.pandulapeter.kubriko.helpers.extensions.rad
import com.pandulapeter.kubriko.types.AngleRadians
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object AngleRadiansSerializer : KSerializer<AngleRadians> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("AngleRadians", PrimitiveKind.FLOAT)

    override fun serialize(encoder: Encoder, value: AngleRadians) {
        encoder.encodeFloat(value.normalized)
    }

    override fun deserialize(decoder: Decoder): AngleRadians {
        return decoder.decodeFloat().rad
    }
}
