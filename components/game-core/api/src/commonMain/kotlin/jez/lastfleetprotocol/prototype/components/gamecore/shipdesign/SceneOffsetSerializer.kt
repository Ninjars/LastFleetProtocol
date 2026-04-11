package jez.lastfleetprotocol.prototype.components.gamecore.shipdesign

import com.pandulapeter.kubriko.helpers.extensions.sceneUnit
import com.pandulapeter.kubriko.types.SceneOffset
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object SceneOffsetSerializer : KSerializer<SceneOffset> {

    @Serializable
    @SerialName("SceneOffset")
    private data class SceneOffsetSurrogate(val x: Float, val y: Float)

    override val descriptor: SerialDescriptor = SceneOffsetSurrogate.serializer().descriptor

    override fun serialize(encoder: Encoder, value: SceneOffset) {
        encoder.encodeSerializableValue(
            SceneOffsetSurrogate.serializer(),
            SceneOffsetSurrogate(value.x.raw, value.y.raw),
        )
    }

    override fun deserialize(decoder: Decoder): SceneOffset {
        val surrogate = decoder.decodeSerializableValue(SceneOffsetSurrogate.serializer())
        return SceneOffset(surrogate.x.sceneUnit, surrogate.y.sceneUnit)
    }
}
