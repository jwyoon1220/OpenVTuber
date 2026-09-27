package io.github.jwyoon1220.core.asset

import io.github.jwyoon1220.api.model.ModelAssetRequest
import io.github.jwyoon1220.api.model.ModelFormat
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ModelSourceResolverTest {
    @Test
    fun `resolves an explicit native-friendly request without reflection`() {
        val root = Files.createTempDirectory("model-resolver-test")
        val source = root.resolve("model/fbx/avatar.fbx")
        Files.createDirectories(source.parent)
        Files.write(source, byteArrayOf(0))

        val resolved = ModelSourceResolver(root).resolve(
            ModelAssetRequest(source = "model/fbx/avatar.fbx", cache = "model/cache/avatar.glb"),
        )

        assertEquals(source, resolved.source)
        assertEquals(root.resolve("model/cache/avatar.glb"), resolved.cache)
        assertEquals(ModelFormat.FBX, resolved.format)
    }

    @Test
    fun `derives a stable glb cache path`() {
        val root = Files.createTempDirectory("model-resolver-test")
        val source = root.resolve("model/fbx/avatar.fbx")
        Files.createDirectories(source.parent)
        Files.write(source, byteArrayOf(0))

        val resolved = ModelSourceResolver(root).resolve(ModelAssetRequest("model/fbx/avatar.fbx"))

        assertEquals(root.resolve("model/cache/avatar.glb"), resolved.cache)
    }

    @Test
    fun `rejects unsupported formats before invoking native code`() {
        val root = Files.createTempDirectory("model-resolver-test")
        val source = root.resolve("avatar.obj")
        Files.write(source, byteArrayOf(0))

        assertFailsWith<IllegalStateException> {
            ModelSourceResolver(root).resolve(ModelAssetRequest("avatar.obj"))
        }
    }
}
