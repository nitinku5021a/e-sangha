package com.digital.sanghaworld.hall.filament

import com.google.android.filament.Box
import com.google.android.filament.Engine
import com.google.android.filament.IndexBuffer
import com.google.android.filament.MathUtils
import com.google.android.filament.VertexBuffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.sin

class GpuMesh(
    val vertexBuffer: VertexBuffer,
    val indexBuffer: IndexBuffer,
    val indexCount: Int,
    val boundingBox: Box
) {
    fun destroy(engine: Engine) {
        engine.destroyVertexBuffer(vertexBuffer)
        engine.destroyIndexBuffer(indexBuffer)
    }
}

class MeshFactory(private val engine: Engine) {
    private val verts = ArrayList<Float>(4096)
    private val indices = ArrayList<Short>(4096)

    fun box(uRepeat: Float = 1f, vRepeat: Float = 1f): GpuMesh {
        clear()
        // 6 faces, each 4 verts with outward normals
        fun face(px: FloatArray, n: FloatArray, u0: Float, v0: Float, u1: Float, v1: Float) {
            val base = (verts.size / 8).toShort()
            add(px[0], px[1], px[2], n[0], n[1], n[2], u0, v0)
            add(px[3], px[4], px[5], n[0], n[1], n[2], u0, v1)
            add(px[6], px[7], px[8], n[0], n[1], n[2], u1, v1)
            add(px[9], px[10], px[11], n[0], n[1], n[2], u1, v0)
            tri(base, (base + 1).toShort(), (base + 2).toShort())
            tri(base, (base + 2).toShort(), (base + 3).toShort())
        }
        val ur = uRepeat
        val vr = vRepeat
        face(floatArrayOf(-1f, -1f, 1f, -1f, 1f, 1f, 1f, 1f, 1f, 1f, -1f, 1f), floatArrayOf(0f, 0f, 1f), 0f, 0f, ur, vr)
        face(floatArrayOf(1f, -1f, -1f, 1f, 1f, -1f, -1f, 1f, -1f, -1f, -1f, -1f), floatArrayOf(0f, 0f, -1f), 0f, 0f, ur, vr)
        face(floatArrayOf(-1f, -1f, -1f, -1f, 1f, -1f, -1f, 1f, 1f, -1f, -1f, 1f), floatArrayOf(-1f, 0f, 0f), 0f, 0f, ur, vr)
        face(floatArrayOf(1f, -1f, 1f, 1f, 1f, 1f, 1f, 1f, -1f, 1f, -1f, -1f), floatArrayOf(1f, 0f, 0f), 0f, 0f, ur, vr)
        face(floatArrayOf(-1f, 1f, 1f, -1f, 1f, -1f, 1f, 1f, -1f, 1f, 1f, 1f), floatArrayOf(0f, 1f, 0f), 0f, 0f, ur, vr)
        face(floatArrayOf(-1f, -1f, -1f, -1f, -1f, 1f, 1f, -1f, 1f, 1f, -1f, -1f), floatArrayOf(0f, -1f, 0f), 0f, 0f, ur, vr)
        return bake(1.05f)
    }

    fun plane(uRepeat: Float, vRepeat: Float): GpuMesh {
        clear()
        add(-1f, 0f, -1f, 0f, 1f, 0f, 0f, 0f)
        add(-1f, 0f, 1f, 0f, 1f, 0f, 0f, vRepeat)
        add(1f, 0f, 1f, 0f, 1f, 0f, uRepeat, vRepeat)
        add(1f, 0f, -1f, 0f, 1f, 0f, uRepeat, 0f)
        tri(0, 1, 2)
        tri(0, 2, 3)
        return bake(1.05f)
    }

    fun cylinder(segments: Int = 24, uRepeat: Float = 2f): GpuMesh {
        clear()
        val top = 1f
        val bot = -1f
        for (i in 0..segments) {
            val a = (i.toFloat() / segments) * (Math.PI * 2).toFloat()
            val x = cos(a)
            val z = sin(a)
            val u = i / segments.toFloat() * uRepeat
            add(x, bot, z, x, 0f, z, u, 0f)
            add(x, top, z, x, 0f, z, u, 1f)
        }
        for (i in 0 until segments) {
            val a = (i * 2).toShort()
            tri(a, (a + 1).toShort(), (a + 3).toShort())
            tri(a, (a + 3).toShort(), (a + 2).toShort())
        }
        val capStart = (verts.size / 8).toShort()
        add(0f, top, 0f, 0f, 1f, 0f, 0.5f, 0.5f)
        add(0f, bot, 0f, 0f, -1f, 0f, 0.5f, 0.5f)
        val topCenter = capStart
        val botCenter = (capStart + 1).toShort()
        val ring = (segments + 1)
        for (i in 0 until segments) {
            val i0 = (i * 2 + 1).toShort()
            val i1 = ((i + 1) * 2 + 1).toShort()
            tri(topCenter, i0, i1)
            val b0 = (i * 2).toShort()
            val b1 = ((i + 1) * 2).toShort()
            tri(botCenter, b1, b0)
        }
        return bake(1.2f)
    }

    fun sphere(slices: Int = 14, stacks: Int = 10): GpuMesh {
        clear()
        for (y in 0..stacks) {
            val v = y / stacks.toFloat()
            val phi = v * Math.PI.toFloat()
            val sy = cos(phi)
            val r = sin(phi)
            for (x in 0..slices) {
                val u = x / slices.toFloat()
                val th = u * (Math.PI * 2).toFloat()
                val px = r * cos(th)
                val pz = r * sin(th)
                add(px, sy, pz, px, sy, pz, u, v)
            }
        }
        val stride = (slices + 1)
        for (y in 0 until stacks) {
            for (x in 0 until slices) {
                val i0 = (y * stride + x).toShort()
                val i1 = (i0 + 1).toShort()
                val i2 = (i0 + stride).toShort()
                val i3 = (i2 + 1).toShort()
                tri(i0, i2, i1)
                tri(i1, i2, i3)
            }
        }
        return bake(1.1f)
    }

    fun seatedFigure(): GpuMesh {
        clear()
        appendCylinder(0f, 0.08f, 0f, 0.20f, 0.07f, 20)
        appendCylinder(0f, 0.30f, 0f, 0.11f, 0.18f, 16)
        appendSphere(0f, 0.55f, 0f, 0.095f, 12, 8)
        return bake(0.8f)
    }

    private fun appendCylinder(cx: Float, cy: Float, cz: Float, radius: Float, halfH: Float, segments: Int) {
        val base = (verts.size / 8)
        for (i in 0..segments) {
            val a = (i.toFloat() / segments) * (Math.PI * 2).toFloat()
            val x = cos(a)
            val z = sin(a)
            val u = i / segments.toFloat()
            add(cx + x * radius, cy - halfH, cz + z * radius, x, 0f, z, u, 0f)
            add(cx + x * radius, cy + halfH, cz + z * radius, x, 0f, z, u, 1f)
        }
        for (i in 0 until segments) {
            val a = (base + i * 2).toShort()
            tri(a, (a + 1).toShort(), (a + 3).toShort())
            tri(a, (a + 3).toShort(), (a + 2).toShort())
        }
        val topC = (verts.size / 8).toShort()
        add(cx, cy + halfH, cz, 0f, 1f, 0f, 0.5f, 0.5f)
        add(cx, cy - halfH, cz, 0f, -1f, 0f, 0.5f, 0.5f)
        for (i in 0 until segments) {
            val i0 = (base + i * 2 + 1).toShort()
            val i1 = (base + (i + 1) * 2 + 1).toShort()
            tri(topC, i0, i1)
            val b0 = (base + i * 2).toShort()
            val b1 = (base + (i + 1) * 2).toShort()
            tri((topC + 1).toShort(), b1, b0)
        }
    }

    private fun appendSphere(cx: Float, cy: Float, cz: Float, radius: Float, slices: Int, stacks: Int) {
        val base = verts.size / 8
        for (y in 0..stacks) {
            val v = y / stacks.toFloat()
            val phi = v * Math.PI.toFloat()
            val sy = cos(phi)
            val r = sin(phi)
            for (x in 0..slices) {
                val u = x / slices.toFloat()
                val th = u * (Math.PI * 2).toFloat()
                val px = r * cos(th)
                val pz = r * sin(th)
                add(cx + px * radius, cy + sy * radius, cz + pz * radius, px, sy, pz, u, v)
            }
        }
        val stride = slices + 1
        for (y in 0 until stacks) {
            for (x in 0 until slices) {
                val i0 = (base + y * stride + x).toShort()
                val i1 = (i0 + 1).toShort()
                val i2 = (i0 + stride).toShort()
                val i3 = (i2 + 1).toShort()
                tri(i0, i2, i1)
                tri(i1, i2, i3)
            }
        }
    }

    private fun add(x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float, u: Float, v: Float) {
        verts += x; verts += y; verts += z
        verts += nx; verts += ny; verts += nz
        verts += u; verts += v
    }

    private fun tri(a: Short, b: Short, c: Short) {
        indices += a; indices += b; indices += c
    }

    private fun clear() {
        verts.clear()
        indices.clear()
    }

    private fun bake(extent: Float): GpuMesh {
        val vertexCount = verts.size / 8
        val vertexSize = 9 * 4
        val data = ByteBuffer.allocate(vertexCount * vertexSize).order(ByteOrder.nativeOrder())
        var i = 0
        val tangent = FloatArray(4)
        while (i < verts.size) {
            val x = verts[i]; val y = verts[i + 1]; val z = verts[i + 2]
            val nx = verts[i + 3]; val ny = verts[i + 4]; val nz = verts[i + 5]
            val u = verts[i + 6]; val v = verts[i + 7]
            var tx = 1f; var ty = 0f; var tz = 0f
            if (kotlin.math.abs(ny) > 0.9f) { tx = 1f; ty = 0f; tz = 0f }
            MathUtils.packTangentFrame(tx, ty, tz, 0f, 0f, 0f, nx, ny, nz, tangent)
            data.putFloat(x).putFloat(y).putFloat(z)
            tangent.forEach { data.putFloat(it) }
            data.putFloat(u).putFloat(v)
            i += 8
        }
        data.flip()
        val vb = VertexBuffer.Builder()
            .bufferCount(1)
            .vertexCount(vertexCount)
            .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT3, 0, vertexSize)
            .attribute(VertexBuffer.VertexAttribute.TANGENTS, 0, VertexBuffer.AttributeType.FLOAT4, 12, vertexSize)
            .attribute(VertexBuffer.VertexAttribute.UV0, 0, VertexBuffer.AttributeType.FLOAT2, 28, vertexSize)
            .build(engine)
        vb.setBufferAt(engine, 0, data)
        val ibData = ByteBuffer.allocate(indices.size * 2).order(ByteOrder.nativeOrder())
        indices.forEach { ibData.putShort(it) }
        ibData.flip()
        val ib = IndexBuffer.Builder()
            .indexCount(indices.size)
            .bufferType(IndexBuffer.Builder.IndexType.USHORT)
            .build(engine)
        ib.setBuffer(engine, ibData)
        return GpuMesh(vb, ib, indices.size, Box(0f, 0f, 0f, extent, extent, extent))
    }
}
