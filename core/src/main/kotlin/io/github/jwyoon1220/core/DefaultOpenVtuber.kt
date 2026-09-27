package io.github.jwyoon1220.core

import de.fabmax.kool.KoolApplication
import de.fabmax.kool.KoolConfigJvm
import de.fabmax.kool.KoolContext
import de.fabmax.kool.NativeAssetLoader
import de.fabmax.kool.math.Vec2i
import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.pipeline.backend.vk.RenderBackendVk
import de.fabmax.kool.scene.defaultOrbitCamera
import de.fabmax.kool.scene.scene
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.FrontendScope
import de.fabmax.kool.util.debugOverlay
import io.github.jwyoon1220.api.OpenVtuber
import io.github.jwyoon1220.api.model.ModelAssetRequest
import io.github.jwyoon1220.core.asset.KoolModelAssetCatalog
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

class DefaultOpenVtuber(
    private val modelRequest: ModelAssetRequest = ModelAssetRequest(
        source = "model/fbx/Neru.fbx",
        cache = "model/cache/Neru.glb",
    ),
    private val modelCatalog: KoolModelAssetCatalog = KoolModelAssetCatalog(),
) : OpenVtuber {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun start() {
        val modelId = modelCatalog.register(modelRequest)

        logger.info("Starting Vulkan renderer with model: {}", modelRequest.source)
        KoolApplication(
            config = KoolConfigJvm(
                defaultAssetLoader = NativeAssetLoader("."),
                renderBackend = RenderBackendVk,
                useOpenGlFallback = false,
                windowTitle = "OpenVTuber Vulkan - ${modelRequest.source.substringAfterLast('/')}",
                windowSize = Vec2i(1280, 720),
                isVsync = true,
            ),
        ) {
            launchScene(ctx, modelId)
        }
    }

    private fun launchScene(ctx: KoolContext, modelId: io.github.jwyoon1220.api.model.ModelAssetId) = FrontendScope.launch {
        ctx.scenes += scene {
            defaultOrbitCamera(yaw = 20f, pitch = -15f).apply {
                setZoom(4.5, min = 0.2, max = 100.0)
            }

            lighting.singleDirectionalLight {
                setup(Vec3f(-1f, -1f, -1f))
                // The source character uses very bright pastel textures. An intensity
                // of 5 clips most albedo values to white in the PBR shader.
                setColor(Color.WHITE, 2f)
            }

            val asset = modelCatalog.load(modelId)
            addNode(asset.model)
            logger.info(
                "Vulkan model loaded: id={}, path={}, bounds={} x {} x {}",
                asset.id.value,
                asset.preparedPath,
                asset.bounds.sizeX,
                asset.bounds.sizeY,
                asset.bounds.sizeZ,
            )
        }
        ctx.scenes += debugOverlay()
    }
}
