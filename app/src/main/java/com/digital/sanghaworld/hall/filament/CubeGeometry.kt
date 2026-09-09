package com.digital.sanghaworld.hall.filament

import com.google.android.filament.Box
import com.google.android.filament.Engine
import com.google.android.filament.IndexBuffer
import com.google.android.filament.MathUtils
import com.google.android.filament.VertexBuffer
import java.nio.ByteBuffer
import java.nio.ByteOrder

class CubeGeometry(engine: Engine) {
    val vertexBuffer: VertexBuffer
    val indexBuffer: IndexBuffer
    val indexCount: Int = 36
    val boundingBox = Box(0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f)

    init {
        val floatSize = 4
        val vertexSize = 7 * floatSize
        val vertexCount = 24
        val tfPX = FloatArray(4)
        val tfNX = FloatArray(4)
        val tfPY = FloatArray(4)
        val tfNY = FloatArray(4)
        val tfPZ = FloatArray(4)
        val tfNZ = FloatArray(4)
        MathUtils.packTangentFrame(0f, 1f, 0f, 0f, 0f, -1f, 1f, 0f, 0f, tfPX)
        MathUtils.packTangentFrame(0f, 1f, 0f, 0f, 0f, -1f, -1f, 0f, 0f, tfNX)
        MathUtils.packTangentFrame(-1f, 0f, 0f, 0f, 0f, -1f, 0f, 1f, 0f, tfPY)
        MathUtils.packTangentFrame(-1f, 0f, 0f, 0f, 0f, 1f, 0f, -1f, 0f, tfNY)
        MathUtils.packTangentFrame(0f, 1f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, tfPZ)
        MathUtils.packTangentFrame(0f, -1f, 0f, 1f, 0f, 0f, 0f, 0f, -1f, tfNZ)

        fun ByteBuffer.putV(x: Float, y: Float, z: Float, t: FloatArray): ByteBuffer {
            putFloat(x); putFloat(y); putFloat(z)
            t.forEach { putFloat(it) }
            return this
        }

        val vertexData = ByteBuffer.allocate(vertexCount * vertexSize).order(ByteOrder.nativeOrder())
            .putV(-1f, -1f, -1f, tfNZ).putV(-1f, 1f, -1f, tfNZ).putV(1f, 1f, -1f, tfNZ).putV(1f, -1f, -1f, tfNZ)
            .putV(1f, -1f, -1f, tfPX).putV(1f, 1f, -1f, tfPX).putV(1f, 1f, 1f, tfPX).putV(1f, -1f, 1f, tfPX)
            .putV(-1f, -1f, 1f, tfPZ).putV(1f, -1f, 1f, tfPZ).putV(1f, 1f, 1f, tfPZ).putV(-1f, 1f, 1f, tfPZ)
            .putV(-1f, -1f, 1f, tfNX).putV(-1f, 1f, 1f, tfNX).putV(-1f, 1f, -1f, tfNX).putV(-1f, -1f, -1f, tfNX)
            .putV(-1f, -1f, 1f, tfNY).putV(-1f, -1f, -1f, tfNY).putV(1f, -1f, -1f, tfNY).putV(1f, -1f, 1f, tfNY)
            .putV(-1f, 1f, -1f, tfPY).putV(-1f, 1f, 1f, tfPY).putV(1f, 1f, 1f, tfPY).putV(1f, 1f, -1f, tfPY)
            .flip()

        vertexBuffer = VertexBuffer.Builder()
            .bufferCount(1)
            .vertexCount(vertexCount)
            .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT3, 0, vertexSize)
            .attribute(VertexBuffer.VertexAttribute.TANGENTS, 0, VertexBuffer.AttributeType.FLOAT4, 3 * floatSize, vertexSize)
            .build(engine)
        vertexBuffer.setBufferAt(engine, 0, vertexData)

        val indexData = ByteBuffer.allocate(indexCount * 2).order(ByteOrder.nativeOrder())
        repeat(6) {
            val i = (it * 4).toShort()
            indexData.putShort(i).putShort((i + 1).toShort()).putShort((i + 2).toShort())
                .putShort(i).putShort((i + 2).toShort()).putShort((i + 3).toShort())
        }
        indexData.flip()
        indexBuffer = IndexBuffer.Builder()
            .indexCount(indexCount)
            .bufferType(IndexBuffer.Builder.IndexType.USHORT)
            .build(engine)
        indexBuffer.setBuffer(engine, indexData)
    }

    fun destroy(engine: Engine) {
        engine.destroyVertexBuffer(vertexBuffer)
        engine.destroyIndexBuffer(indexBuffer)
    }
}
