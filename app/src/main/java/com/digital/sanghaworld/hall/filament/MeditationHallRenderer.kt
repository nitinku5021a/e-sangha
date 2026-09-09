package com.digital.sanghaworld.hall.filament

import android.content.Context
import android.opengl.Matrix
import android.view.Choreographer
import android.view.Surface
import android.view.SurfaceView
import com.digital.sanghaworld.hall.MeditationHallState
import com.digital.sanghaworld.hall.SeatLayout
import com.google.android.filament.Camera
import com.google.android.filament.Colors
import com.google.android.filament.Engine
import com.google.android.filament.Entity
import com.google.android.filament.EntityManager
import com.google.android.filament.Filament
import com.google.android.filament.LightManager
import com.google.android.filament.Material
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderableManager
import com.google.android.filament.Renderer
import com.google.android.filament.Scene
import com.google.android.filament.Skybox
import com.google.android.filament.SwapChain
import com.google.android.filament.Texture
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.android.DisplayHelper
import com.google.android.filament.android.FilamentHelper
import com.google.android.filament.android.UiHelper
import com.google.android.filament.filamat.MaterialBuilder
import kotlin.math.sin

class MeditationHallRenderer(
    context: Context,
    private val surfaceView: SurfaceView
) {
    companion object {
        init { Filament.init() }
    }

    private val choreographer = Choreographer.getInstance()
    private val displayHelper = DisplayHelper(context)
    private val uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
    private val engine: Engine = Engine.create()
    private val renderer: Renderer = engine.createRenderer()
    private val scene: Scene = engine.createScene()
    private val view: View = engine.createView()
    private val camera: Camera = engine.createCamera(engine.entityManager.create())
    private var swapChain: SwapChain? = null
    private val meshes = MeshFactory(engine)
    private val box = meshes.box(1f, 1f)
    private val plane = meshes.plane(8f, 10f)
    private val cylinder = meshes.cylinder(28, 1f)
    private val sphere = meshes.sphere(16, 12)
    private val figure = meshes.seatedFigure()
    private val textures = HallTextures(context, engine)
    private val lit: Material
    private val emissive: Material
    private val instances = ArrayList<MaterialInstance>()
    private val entities = ArrayList<Int>()
    @Entity private var sun = 0
    @Entity private var fill = 0
    @Entity private var altarLamp = 0

    private data class SeatVisual(
        val seatId: String,
        val x: Float,
        val z: Float,
        val sitter: Int,
        val mat: MaterialInstance,
        var visible: Boolean,
        var alpha: Float,
        val phase: Float
    )

    private val seats = ArrayList<SeatVisual>()
    private var hallState = MeditationHallState("", emptyList())
    private var destroyed = false

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (destroyed) return
            choreographer.postFrameCallback(this)
            animate(frameTimeNanos)
            if (uiHelper.isReadyToRender) {
                val chain = swapChain ?: return
                if (renderer.beginFrame(chain, frameTimeNanos)) {
                    renderer.render(view)
                    renderer.endFrame()
                }
            }
        }
    }

    init {
        MaterialBuilder.init()
        lit = compile(
            "hall_lit",
            MaterialBuilder.Shading.LIT,
            MaterialBuilder.BlendingMode.TRANSPARENT,
            """
            void material(inout MaterialInputs material) {
                prepareMaterial(material);
                vec4 c = texture(materialParams_albedo, getUV0());
                material.baseColor = vec4(c.rgb, materialParams.opacity);
                material.roughness = 0.72;
                material.metallic = 0.0;
            }
            """.trimIndent()
        )
        emissive = compile(
            "hall_emit",
            MaterialBuilder.Shading.UNLIT,
            MaterialBuilder.BlendingMode.OPAQUE,
            """
            void material(inout MaterialInputs material) {
                prepareMaterial(material);
                material.baseColor = vec4(materialParams.emissive, 1.0);
            }
            """.trimIndent(),
            textured = false
        )
        MaterialBuilder.shutdown()
        setupView()
        setupLights()
        buildArchitecture()
        buildSeats()
        uiHelper.renderCallback = object : UiHelper.RendererCallback {
            override fun onNativeWindowChanged(surface: Surface) {
                swapChain?.let { engine.destroySwapChain(it) }
                swapChain = engine.createSwapChain(surface)
                displayHelper.attach(renderer, surfaceView.display)
            }
            override fun onDetachedFromSurface() {
                displayHelper.detach()
                swapChain?.let {
                    engine.destroySwapChain(it)
                    engine.flushAndWait()
                    swapChain = null
                }
            }
            override fun onResized(width: Int, height: Int) {
                val aspect = width.toDouble() / height.toDouble().coerceAtLeast(1.0)
                camera.setProjection(32.0, aspect, 0.2, 50.0, Camera.Fov.VERTICAL)
                view.viewport = Viewport(0, 0, width, height)
                FilamentHelper.synchronizePendingFrames(engine)
            }
        }
        uiHelper.attachTo(surfaceView)
        camera.lookAt(0.0, 4.6, 8.4, 0.0, 0.55, -5.2, 0.0, 1.0, 0.0)
        camera.setExposure(16.0f, 1.0f / 125.0f, 100.0f)
        choreographer.postFrameCallback(frameCallback)
    }

    fun setState(state: MeditationHallState) { hallState = state }

    fun destroy() {
        if (destroyed) return
        destroyed = true
        choreographer.removeFrameCallback(frameCallback)
        uiHelper.detach()
        entities.forEach { engine.destroyEntity(it) }
        engine.destroyEntity(sun)
        engine.destroyEntity(fill)
        engine.destroyEntity(altarLamp)
        instances.forEach { engine.destroyMaterialInstance(it) }
        engine.destroyMaterial(lit)
        engine.destroyMaterial(emissive)
        listOf(box, plane, cylinder, sphere, figure).forEach { it.destroy(engine) }
        textures.destroy(engine)
        engine.destroyRenderer(renderer)
        engine.destroyView(view)
        engine.destroyScene(scene)
        engine.destroyCameraComponent(camera.entity)
        val em = EntityManager.get()
        entities.forEach { em.destroy(it) }
        em.destroy(sun); em.destroy(fill); em.destroy(altarLamp); em.destroy(camera.entity)
        engine.destroy()
    }

    private fun compile(
        name: String,
        shading: MaterialBuilder.Shading,
        blending: MaterialBuilder.BlendingMode,
        body: String,
        textured: Boolean = true
    ): Material {
        val b = MaterialBuilder()
            .platform(MaterialBuilder.Platform.MOBILE)
            .name(name)
            .shading(shading)
            .blending(blending)
            .optimization(MaterialBuilder.Optimization.NONE)
            .material(body)
        if (textured) {
            b.require(MaterialBuilder.VertexAttribute.UV0)
            b.samplerParameter(
                MaterialBuilder.SamplerType.SAMPLER_2D,
                MaterialBuilder.SamplerFormat.FLOAT,
                MaterialBuilder.ParameterPrecision.DEFAULT,
                "albedo"
            )
            b.uniformParameter(MaterialBuilder.UniformType.FLOAT, "opacity")
        } else {
            b.uniformParameter(MaterialBuilder.UniformType.FLOAT3, "emissive")
        }
        val pkg = b.build(engine)
        check(pkg.isValid) { "Material $name failed." }
        val buf = pkg.buffer
        return Material.Builder().payload(buf, buf.remaining()).build(engine)
    }

    private fun setupView() {
        scene.skybox = Skybox.Builder().color(0.83f, 0.86f, 0.88f, 1.0f).build(engine)
        view.camera = camera
        view.scene = scene
        view.setShadowingEnabled(true)
    }

    private fun setupLights() {
        val em = EntityManager.get()
        sun = em.create()
        val (r, g, b) = Colors.cct(4_800.0f)
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(r, g, b)
            .intensity(42_000.0f)
            .direction(0.55f, -0.75f, -0.25f)
            .castShadows(true)
            .build(engine, sun)
        scene.addEntity(sun)
        fill = em.create()
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(1.0f, 0.96f, 0.90f)
            .intensity(12_000.0f)
            .direction(-0.35f, -1.0f, 0.15f)
            .castShadows(false)
            .build(engine, fill)
        scene.addEntity(fill)
        altarLamp = em.create()
        LightManager.Builder(LightManager.Type.POINT)
            .color(1.0f, 0.84f, 0.62f)
            .intensity(8_000.0f)
            .position(0f, 2.2f, -6.6f)
            .falloff(14f)
            .castShadows(false)
            .build(engine, altarLamp)
        scene.addEntity(altarLamp)
    }

    private fun buildArchitecture() {
        place(plane, textures.woodFloor, 0f, 0f, -1.2f, 7.2f, 1f, 9.0f, 1f)
        place(plane, textures.tatami, -2.35f, 0.01f, -1.0f, 2.5f, 1f, 7.4f, 1f)
        place(plane, textures.tatami, 2.35f, 0.01f, -1.0f, 2.5f, 1f, 7.4f, 1f)
        place(box, textures.woodWall, 0f, 2.6f, -8.4f, 7.2f, 2.7f, 0.08f, 1f)
        place(box, textures.woodWall, -7.2f, 2.6f, -1.2f, 0.08f, 2.7f, 9.0f, 1f)
        place(box, textures.woodWall, 7.2f, 2.6f, -1.2f, 0.08f, 2.7f, 9.0f, 1f)
        place(plane, textures.woodWall, 0f, 5.35f, -1.2f, 7.2f, 1f, 9.0f, 1f)
        for (i in 0..8) {
            val z = -8.0f + i * 1.7f
            place(box, textures.woodFloor, 0f, 5.15f, z, 7.1f, 0.06f, 0.10f, 1f)
        }
        for (x in listOf(-3.9f, 3.9f)) {
            place(cylinder, textures.woodFloor, x, 2.3f, -0.4f, 0.16f, 2.3f, 0.16f, 1f)
            place(cylinder, textures.woodFloor, x, 2.3f, 3.4f, 0.16f, 2.3f, 0.16f, 1f)
        }
        place(box, textures.plaster, 0f, 0.42f, -7.35f, 1.5f, 0.42f, 0.55f, 1f)
        place(cylinder, textures.plaster, 0f, 1.15f, -7.35f, 0.18f, 0.32f, 0.18f, 1f)
        place(sphere, textures.plaster, 0f, 1.58f, -7.35f, 0.16f, 0.16f, 0.16f, 1f)
        emitWindow(-2.35f, 2.4f, -8.28f, 1.05f, 1.7f)
        emitWindow(2.35f, 2.4f, -8.28f, 1.05f, 1.7f)
        emitWindow(-7.12f, 2.5f, -3.2f, 0.04f, 1.5f, 2.2f)
        emitWindow(7.12f, 2.5f, -3.2f, 0.04f, 1.5f, 2.2f)
    }

    private fun emitWindow(x: Float, y: Float, z: Float, sx: Float, sy: Float, sz: Float = 0.04f) {
        val mat = emissive.createInstance()
        mat.setParameter("emissive", 0.95f, 0.97f, 0.99f)
        instances += mat
        spawn(box, mat, x, y, z, sx, sy, sz, shadows = false)
    }

    private fun buildSeats() {
        SeatLayout.slots.forEachIndexed { index, slot ->
            place(cylinder, textures.beige, slot.x, 0.07f, slot.z, 0.32f, 0.07f, 0.32f, 1f)
            val mat = textured(textures.robe, 0f)
            val sitter = spawn(figure, mat, slot.x, 0.08f, slot.z, 1f, 1f, 1f, shadows = true)
            scene.removeEntity(sitter)
            seats += SeatVisual(slot.id, slot.x, slot.z, sitter, mat, false, 0f, index * 0.63f)
        }
    }

    private fun animate(frameTimeNanos: Long) {
        val t = frameTimeNanos / 1_000_000_000.0
        val occupied = hallState.occupants.associateBy { it.seatId }
        seats.forEach { seat ->
            val occ = occupied[seat.seatId]
            val target = if (occ != null) 1f else 0f
            seat.alpha += (target - seat.alpha) * 0.08f
            if (seat.alpha < 0.02f && seat.visible) {
                scene.removeEntity(seat.sitter)
                seat.visible = false
            } else if (seat.alpha >= 0.02f && !seat.visible) {
                scene.addEntity(seat.sitter)
                seat.visible = true
            }
            seat.mat.setParameter("opacity", seat.alpha)
            if (seat.visible) {
                val breath = 1f + 0.016f * sin(t * 1.12 + seat.phase).toFloat()
                setTransform(seat.sitter, seat.x, 0.08f, seat.z, 1f, breath, 1f)
            }
        }
    }

    private fun place(
        mesh: GpuMesh, tex: Texture,
        x: Float, y: Float, z: Float,
        sx: Float, sy: Float, sz: Float,
        opacity: Float
    ) {
        spawn(mesh, textured(tex, opacity), x, y, z, sx, sy, sz, shadows = true)
    }

    private fun spawn(
        mesh: GpuMesh, mat: MaterialInstance,
        x: Float, y: Float, z: Float,
        sx: Float, sy: Float, sz: Float,
        shadows: Boolean
    ): Int {
        val e = EntityManager.get().create()
        RenderableManager.Builder(1)
            .boundingBox(mesh.boundingBox)
            .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, mesh.vertexBuffer, mesh.indexBuffer, 0, mesh.indexCount)
            .material(0, mat)
            .castShadows(shadows)
            .receiveShadows(true)
            .culling(false)
            .build(engine, e)
        setTransform(e, x, y, z, sx, sy, sz)
        scene.addEntity(e)
        entities += e
        return e
    }

    private fun textured(tex: Texture, opacity: Float): MaterialInstance {
        val inst = lit.createInstance()
        inst.setParameter("albedo", tex, textures.sampler)
        inst.setParameter("opacity", opacity)
        instances += inst
        return inst
    }

    private fun setTransform(entity: Int, x: Float, y: Float, z: Float, sx: Float, sy: Float, sz: Float) {
        val m = FloatArray(16)
        Matrix.setIdentityM(m, 0)
        Matrix.translateM(m, 0, x, y, z)
        Matrix.scaleM(m, 0, sx, sy, sz)
        val tm = engine.transformManager
        tm.setTransform(tm.getInstance(entity), m)
    }
}
