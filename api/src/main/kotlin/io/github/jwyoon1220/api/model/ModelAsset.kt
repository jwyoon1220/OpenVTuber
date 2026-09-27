package io.github.jwyoon1220.api.model

/** Stable, renderer-independent model identifier suitable for packed tables. */
@JvmInline
value class ModelAssetId(val value: Int) {
    init {
        require(value >= 0) { "ModelAssetId must be non-negative" }
    }
}

enum class ModelFormat(val extension: String) {
    FBX("fbx"),
    GLB("glb"),
    GLTF("gltf"),
}

/**
 * Native-friendly load description. It deliberately contains no reflection-based type tokens,
 * java.io.File, renderer objects, or boxed vector classes.
 */
data class ModelAssetRequest(
    val source: String,
    val cache: String? = null,
    val targetSize: Float = 3f,
    val animations: Boolean = false,
    val skins: Boolean = false,
    val morphTargets: Boolean = false,
) {
    init {
        require(source.isNotBlank()) { "Model source must not be blank" }
        require(targetSize.isFinite() && targetSize > 0f) { "targetSize must be finite and positive" }
    }
}

data class ModelBounds(
    val minX: Float,
    val minY: Float,
    val minZ: Float,
    val maxX: Float,
    val maxY: Float,
    val maxZ: Float,
) {
    val sizeX: Float get() = maxX - minX
    val sizeY: Float get() = maxY - minY
    val sizeZ: Float get() = maxZ - minZ
    val maxDimension: Float get() = maxOf(sizeX, sizeY, sizeZ)
    val centerX: Float get() = (minX + maxX) * 0.5f
    val centerY: Float get() = (minY + maxY) * 0.5f
    val centerZ: Float get() = (minZ + maxZ) * 0.5f
}

enum class ModelAssetState {
    REGISTERED,
    PREPARED,
    LOADED,
    RELEASED,
    FAILED,
}

interface ModelAssetCatalog {
    fun register(request: ModelAssetRequest): ModelAssetId
    fun state(id: ModelAssetId): ModelAssetState
    fun bounds(id: ModelAssetId): ModelBounds?
    fun release(id: ModelAssetId): Boolean
}
