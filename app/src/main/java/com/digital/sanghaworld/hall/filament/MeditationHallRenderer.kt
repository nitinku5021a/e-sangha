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
        init {
            Filament.init()
        }
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
    private val cube: CubeGeometry = CubeGeometry(engine)
    private val material: Material
    private val instances = ArrayList<MaterialInstance>()
    private val entities = ArrayList<Int>()
    @Entity private var sun = 0
    @Entity private var lamp = 0

    private data class SeatVisual(
        val seatId: String,
        val x: Float,
        val z: Float,
        val sitter: Int,
        val sitterMat: MaterialInstance,
        val baseY: Float,
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
        material = buildLitMaterial()
        MaterialBuilder.shutdown()
        setupView()
        setupLights()
        buildHall()
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
                camera.setProjection(28.0, aspect, 0.15, 40.0, Camera.Fov.VERTICAL)
                view.viewport = Viewport(0, 0, width, height)
                FilamentHelper.synchronizePendingFrames(engine)
            }
        }
        uiHelper.attachTo(surfaceView)
        camera.lookAt(0.0, 5.4, 9.2, 0.0, 0.35, -2.4, 0.0, 1.0, 0.0)
        camera.setExposure(16.0f, 1.0f / 125.0f, 100.0f)
        choreographer.postFrameCallback(frameCallback)
    }

    fun setState(state: MeditationHallState) {
        hallState = state
    }

    fun destroy() {
        if (destroyed) return
        destroyed = true
        choreographer.removeFrameCallback(frameCallback)
        uiHelper.detach()
        entities.forEach { engine.destroyEntity(it) }
        engine.destroyEntity(sun)
        engine.destroyEntity(lamp)
        instances.forEach { engine.destroyMaterialInstance(it) }
        engine.destroyMaterial(material)
        cube.destroy(engine)
        engine.destroyRenderer(renderer)
        engine.destroyView(view)
        engine.destroyScene(scene)
        engine.destroyCameraComponent(camera.entity)
        val em = EntityManager.get()
        entities.forEach { em.destroy(it) }
        em.destroy(sun)
        em.destroy(lamp)
        em.destroy(camera.entity)
        engine.destroy()
    }

    private fun buildLitMaterial(): Material {
        val pkg = MaterialBuilder()
            .platform(MaterialBuilder.Platform.MOBILE)
            .name("hall_lit")
            .shading(MaterialBuilder.Shading.LIT)
            .blending(MaterialBuilder.BlendingMode.TRANSPARENT)
            .uniformParameter(MaterialBuilder.UniformType.FLOAT4, "baseColor")
            .material(
                """
                void material(inout MaterialInputs material) {
                    prepareMaterial(material);
                    material.baseColor = materialParams.baseColor;
                    material.roughness = 0.82;
                    material.metallic = 0.0;
                }
                """.trimIndent()
            )
            .optimization(MaterialBuilder.Optimization.NONE)
            .build(engine)
        check(pkg.isValid) { "Hall material failed to compile." }
        val buffer = pkg.buffer
        return Material.Builder().payload(buffer, buffer.remaining()).build(engine)
    }

    private fun setupView() {
        scene.skybox = Skybox.Builder().color(0.10f, 0.09f, 0.07f, 1.0f).build(engine)
        view.camera = camera
        view.scene = scene
    }

    private fun setupLights() {
        val em = EntityManager.get()
        sun = em.create()
        val (r, g, b) = Colors.cct(4_200.0f)
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(r, g, b)
            .intensity(28_000.0f)
            .direction(0.28f, -1.0f, -0.35f)
            .castShadows(false)
            .build(engine, sun)
        scene.addEntity(sun)

        lamp = em.create()
        LightManager.Builder(LightManager.Type.POINT)
            .color(1.0f, 0.82f, 0.55f)
            .intensity(6_000.0f)
            .position(0.0f, 1.8f, -6.4f)
            .falloff(12.0f)
            .castShadows(false)
            .build(engine, lamp)
        scene.addEntity(lamp)
    }

    private fun buildHall() {
        addBox(0f, -0.04f, -1.2f, 6.4f, 0.04f, 8.2f, 0.42f, 0.32f, 0.22f, 1f)
        addBox(0f, 1.4f, -8.0f, 6.4f, 1.5f, 0.08f, 0.38f, 0.30f, 0.22f, 1f)
        addBox(-6.3f, 1.4f, -1.2f, 0.08f, 1.5f, 8.2f, 0.36f, 0.28f, 0.20f, 1f)
        addBox(6.3f, 1.4f, -1.2f, 0.08f, 1.5f, 8.2f, 0.36f, 0.28f, 0.20f, 1f)
        addBox(0f, 0.28f, -6.6f, 1.6f, 0.28f, 0.55f, 0.28f, 0.18f, 0.10f, 1f)
        addBox(0f, 0.85f, -6.55f, 0.22f, 0.32f, 0.22f, 0.72f, 0.58f, 0.28f, 1f)
        addBox(-0.55f, 0.62f, -6.45f, 0.06f, 0.18f, 0.06f, 0.95f, 0.78f, 0.42f, 1f)
        addBox(0.55f, 0.62f, -6.45f, 0.06f, 0.18f, 0.06f, 0.95f, 0.78f, 0.42f, 1f)
    }

    private fun buildSeats() {
        val cols = SeatLayout.COLUMNS
        val rows = SeatLayout.ROWS
        val x0 = -(cols - 1) * 0.95f / 2f
        val z0 = -4.4f
        SeatLayout.seatIds.forEachIndexed { index, id ->
            val col = index % cols
            val row = index / cols
            val x = x0 + col * 0.95f
            val z = z0 + row * 1.15f
            addBox(x, 0.07f, z, 0.32f, 0.07f, 0.32f, 0.45f, 0.28f, 0.18f, 1f)
            val sitterMat = tint(0.18f, 0.16f, 0.14f, 0f)
            val sitter = addBoxEntity(x, 0.42f, z, 0.16f, 0.28f, 0.16f, sitterMat)
            seats += SeatVisual(
                seatId = id,
                x = x,
                z = z,
                sitter = sitter,
                sitterMat = sitterMat,
                baseY = 0.42f,
                visible = false,
                alpha = 0f,
                phase = (index * 0.7f)
            )
            scene.removeEntity(sitter)
        }
    }

    private fun animate(frameTimeNanos: Long) {
        val t = frameTimeNanos / 1_000_000_000.0
        val occupied = hallState.occupants.associateBy { it.seatId }
        seats.forEach { seat ->
            val occupant = occupied[seat.seatId]
            val wantVisible = occupant != null
            val target = if (wantVisible) 1f else 0f
            seat.alpha += (target - seat.alpha) * 0.08f
            if (seat.alpha < 0.02f && seat.visible) {
                scene.removeEntity(seat.sitter)
                seat.visible = false
            } else if (seat.alpha >= 0.02f && !seat.visible) {
                scene.addEntity(seat.sitter)
                seat.visible = true
            }
            if (occupant?.isCurrentUser == true) {
                seat.sitterMat.setParameter("baseColor", 0.42f, 0.32f, 0.18f, seat.alpha)
            } else {
                seat.sitterMat.setParameter("baseColor", 0.16f, 0.14f, 0.12f, seat.alpha)
            }
            if (seat.visible) {
                val breath = 1f + 0.018f * sin(t * 1.15 + seat.phase.toDouble()).toFloat()
                setTransform(seat.sitter, seat.x, seat.baseY * breath, seat.z, 0.16f, 0.28f * breath, 0.16f)
            }
        }
    }

    private fun addBox(
        x: Float, y: Float, z: Float,
        sx: Float, sy: Float, sz: Float,
        r: Float, g: Float, b: Float, a: Float
    ): Int = addBoxEntity(x, y, z, sx, sy, sz, tint(r, g, b, a))

    private fun addBoxEntity(
        x: Float, y: Float, z: Float,
        sx: Float, sy: Float, sz: Float,
        mat: MaterialInstance
    ): Int {
        val entity = EntityManager.get().create()
        RenderableManager.Builder(1)
            .boundingBox(cube.boundingBox)
            .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, cube.vertexBuffer, cube.indexBuffer, 0, cube.indexCount)
            .material(0, mat)
            .culling(false)
            .build(engine, entity)
        setTransform(entity, x, y, z, sx, sy, sz)
        scene.addEntity(entity)
        entities += entity
        return entity
    }

    private fun setTransform(entity: Int, x: Float, y: Float, z: Float, sx: Float, sy: Float, sz: Float) {
        val m = FloatArray(16)
        Matrix.setIdentityM(m, 0)
        Matrix.translateM(m, 0, x, y, z)
        Matrix.scaleM(m, 0, sx, sy, sz)
        val tm = engine.transformManager
        tm.setTransform(tm.getInstance(entity), m)
    }

    private fun tint(r: Float, g: Float, b: Float, a: Float): MaterialInstance {
        val inst = material.createInstance()
        inst.setParameter("baseColor", r, g, b, a)
        instances += inst
        return inst
    }
}
