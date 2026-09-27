package io.github.jwyoon1220.core.asset

import io.github.jwyoon1220.api.model.ModelFormat
import java.nio.file.Path

fun interface ModelPreprocessor {
    fun prepare(source: ResolvedModelSource): Path
}

class NativeModelPreprocessor : ModelPreprocessor {
    override fun prepare(source: ResolvedModelSource): Path = when (source.format) {
        ModelFormat.FBX -> FbxConverter.convertIfNeeded(source.source, source.cache)
        ModelFormat.GLB, ModelFormat.GLTF -> source.source
    }
}
