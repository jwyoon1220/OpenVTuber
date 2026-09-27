package io.github.jwyoon1220.core.asset

import io.github.jwyoon1220.api.model.ModelAssetRequest
import io.github.jwyoon1220.api.model.ModelFormat
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension

data class ResolvedModelSource(
    val source: Path,
    val cache: Path,
    val format: ModelFormat,
)

class ModelSourceResolver(private val workingDirectory: Path = Path.of(".")) {
    fun resolve(request: ModelAssetRequest): ResolvedModelSource {
        val source = workingDirectory.resolve(request.source).normalize()
        require(Files.isRegularFile(source)) { "Model file does not exist: ${source.toAbsolutePath()}" }

        val format = ModelFormat.entries.firstOrNull { it.extension.equals(source.extension, ignoreCase = true) }
            ?: error("Unsupported model format '.${source.extension}': $source")
        val cache = request.cache?.let { workingDirectory.resolve(it).normalize() }
            ?: defaultCachePath(source, format)
        return ResolvedModelSource(source, cache, format)
    }

    private fun defaultCachePath(source: Path, format: ModelFormat): Path {
        if (format == ModelFormat.GLB) return source
        val fileName = source.fileName.toString().substringBeforeLast('.') + ".glb"
        val modelRoot = source.parent?.parent ?: workingDirectory
        return modelRoot.resolve("cache").resolve(fileName).normalize()
    }
}
