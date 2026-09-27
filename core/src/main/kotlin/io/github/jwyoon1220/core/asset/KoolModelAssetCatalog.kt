package io.github.jwyoon1220.core.asset

import de.fabmax.kool.Assets
import de.fabmax.kool.modules.gltf.GltfLoadConfig
import de.fabmax.kool.modules.gltf.loadGltfModel
import de.fabmax.kool.modules.ksl.KslLitShader
import de.fabmax.kool.pipeline.CullMethod
import de.fabmax.kool.pipeline.shading.AlphaMode
import de.fabmax.kool.scene.Model
import de.fabmax.kool.util.Color
import io.github.jwyoon1220.api.model.ModelAssetCatalog
import io.github.jwyoon1220.api.model.ModelAssetId
import io.github.jwyoon1220.api.model.ModelAssetRequest
import io.github.jwyoon1220.api.model.ModelAssetState
import io.github.jwyoon1220.api.model.ModelBounds
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import java.nio.file.Path

data class KoolModelAsset(
    val id: ModelAssetId,
    val request: ModelAssetRequest,
    val preparedPath: Path,
    val model: Model,
    val bounds: ModelBounds,
) {
    fun frame() {
        val scale = request.targetSize / bounds.maxDimension
        model.transform.setIdentity()
        model.transform.scale(scale)
        model.transform.translate(
            -bounds.centerX * scale,
            -bounds.centerY * scale,
            -bounds.centerZ * scale,
        )
    }
}

/**
 * Handle-based model storage. Metadata and bounds are held in structure-of-arrays form so hot
 * iteration does not allocate. Kool objects stay in a separate cold object column.
 *
 * Registration can happen from any thread. GPU loading must happen on Kool's render coroutine.
 */
class KoolModelAssetCatalog(
    private val resolver: ModelSourceResolver = ModelSourceResolver(),
    private val preprocessor: ModelPreprocessor = NativeModelPreprocessor(),
) : ModelAssetCatalog {
    private val idsByKey = Object2IntOpenHashMap<String>().apply { defaultReturnValue(-1) }
    private var requests = arrayOfNulls<ModelAssetRequest>(INITIAL_CAPACITY)
    private var preparedPaths = arrayOfNulls<Path>(INITIAL_CAPACITY)
    private var models = arrayOfNulls<Model>(INITIAL_CAPACITY)
    private var states = ByteArray(INITIAL_CAPACITY)
    private var boundsData = FloatArray(INITIAL_CAPACITY * BOUNDS_STRIDE)
    private var size = 0

    @Synchronized
    override fun register(request: ModelAssetRequest): ModelAssetId {
        val key = request.cache ?: request.source
        val existing = idsByKey.getInt(key)
        if (existing >= 0 && states[existing].toInt() != STATE_RELEASED) return ModelAssetId(existing)

        ensureCapacity(size + 1)
        val id = size++
        requests[id] = request
        states[id] = STATE_REGISTERED.toByte()
        idsByKey.put(key, id)
        return ModelAssetId(id)
    }

    suspend fun load(id: ModelAssetId): KoolModelAsset {
        val index = checkedIndex(id)
        models[index]?.let { model ->
            return KoolModelAsset(id, requireNotNull(requests[index]), requireNotNull(preparedPaths[index]), model, readBounds(index))
        }

        val request = requireNotNull(requests[index])
        return try {
            val prepared = preprocessor.prepare(resolver.resolve(request))
            preparedPaths[index] = prepared
            states[index] = STATE_PREPARED.toByte()
            val assetPath = prepared.toString().replace('\\', '/')
            val model = Assets.loadGltfModel(assetPath, loadConfig(request)).getOrThrow()
            val bounds = calculateBounds(model)
            writeBounds(index, bounds)
            models[index] = model
            states[index] = STATE_LOADED.toByte()
            KoolModelAsset(id, request, prepared, model, bounds).also { it.frame() }
        } catch (failure: Throwable) {
            states[index] = STATE_FAILED.toByte()
            throw failure
        }
    }

    @Synchronized
    override fun state(id: ModelAssetId): ModelAssetState = when (states[checkedIndex(id)].toInt()) {
        STATE_REGISTERED -> ModelAssetState.REGISTERED
        STATE_PREPARED -> ModelAssetState.PREPARED
        STATE_LOADED -> ModelAssetState.LOADED
        STATE_RELEASED -> ModelAssetState.RELEASED
        else -> ModelAssetState.FAILED
    }

    @Synchronized
    override fun bounds(id: ModelAssetId): ModelBounds? {
        val index = checkedIndex(id)
        return if (states[index].toInt() == STATE_LOADED) readBounds(index) else null
    }

    @Synchronized
    override fun release(id: ModelAssetId): Boolean {
        val index = checkedIndex(id)
        if (states[index].toInt() == STATE_RELEASED) return false
        models[index]?.release()
        models[index] = null
        preparedPaths[index] = null
        states[index] = STATE_RELEASED.toByte()
        return true
    }

    private fun loadConfig(request: ModelAssetRequest) = GltfLoadConfig(
        applyMaterials = true,
        loadAnimations = request.animations,
        applyMorphTargets = request.morphTargets,
        applySkins = request.skins,
        pbrBlock = {
            pipeline { cullMethod = CullMethod.NO_CULLING }
            alphaMode = AlphaMode.Mask(0.05f)
            lighting {
                ambientLight = KslLitShader.AmbientLight.Uniform(Color(0.35f, 0.35f, 0.35f).toLinear())
            }
        },
    )

    private fun calculateBounds(model: Model): ModelBounds {
        require(model.meshes.isNotEmpty()) { "Loaded model contains no meshes" }
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var minZ = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        var maxZ = Float.NEGATIVE_INFINITY
        model.meshes.values.forEach { mesh ->
            mesh.geometry.forEach { vertex ->
                val p = vertex.position
                minX = minOf(minX, p.x)
                minY = minOf(minY, p.y)
                minZ = minOf(minZ, p.z)
                maxX = maxOf(maxX, p.x)
                maxY = maxOf(maxY, p.y)
                maxZ = maxOf(maxZ, p.z)
            }
        }
        return ModelBounds(minX, minY, minZ, maxX, maxY, maxZ).also {
            require(it.maxDimension.isFinite() && it.maxDimension > 0.0001f) { "Loaded model has invalid bounds" }
        }
    }

    private fun writeBounds(index: Int, value: ModelBounds) {
        val offset = index * BOUNDS_STRIDE
        boundsData[offset] = value.minX
        boundsData[offset + 1] = value.minY
        boundsData[offset + 2] = value.minZ
        boundsData[offset + 3] = value.maxX
        boundsData[offset + 4] = value.maxY
        boundsData[offset + 5] = value.maxZ
    }

    private fun readBounds(index: Int): ModelBounds {
        val offset = index * BOUNDS_STRIDE
        return ModelBounds(
            boundsData[offset], boundsData[offset + 1], boundsData[offset + 2],
            boundsData[offset + 3], boundsData[offset + 4], boundsData[offset + 5],
        )
    }

    private fun checkedIndex(id: ModelAssetId): Int = id.value.also {
        require(it < size) { "Unknown model asset id: ${id.value}" }
    }

    private fun ensureCapacity(required: Int) {
        if (required <= requests.size) return
        val capacity = maxOf(required, requests.size * 2)
        requests = requests.copyOf(capacity)
        preparedPaths = preparedPaths.copyOf(capacity)
        models = models.copyOf(capacity)
        states = states.copyOf(capacity)
        boundsData = boundsData.copyOf(capacity * BOUNDS_STRIDE)
    }

    private companion object {
        const val INITIAL_CAPACITY = 16
        const val BOUNDS_STRIDE = 6
        const val STATE_REGISTERED = 0
        const val STATE_PREPARED = 1
        const val STATE_LOADED = 2
        const val STATE_RELEASED = 3
        const val STATE_FAILED = 4
    }
}
