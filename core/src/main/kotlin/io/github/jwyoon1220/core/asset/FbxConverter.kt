package io.github.jwyoon1220.core.asset

import org.lwjgl.assimp.Assimp
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path

object FbxConverter {
    private const val CACHE_VERSION = "6"
    private const val GLTF_JSON_CHUNK = 0x4E4F534A
    private const val IMPORT_FLAGS =
        Assimp.aiProcess_Triangulate or
            Assimp.aiProcess_JoinIdenticalVertices or
            Assimp.aiProcess_GenSmoothNormals or
            Assimp.aiProcess_LimitBoneWeights or
            Assimp.aiProcess_PreTransformVertices

    fun convertIfNeeded(source: Path, target: Path): Path {
        val versionFile = target.resolveSibling("${target.fileName}.version")
        val cacheIsCurrent = Files.isRegularFile(target) &&
            Files.isRegularFile(versionFile) &&
            Files.readString(versionFile) == CACHE_VERSION &&
            Files.getLastModifiedTime(target) >= Files.getLastModifiedTime(source)
        if (cacheIsCurrent) {
            return target
        }

        Files.createDirectories(target.parent)
        val scene = Assimp.aiImportFile(source.toAbsolutePath().toString(), IMPORT_FLAGS)
            ?: error("Assimp failed to import $source: ${Assimp.aiGetErrorString()}")

        try {
            val result = Assimp.aiExportScene(scene, "glb2", target.toAbsolutePath().toString(), 0)
            check(result == Assimp.aiReturn_SUCCESS) {
                "Assimp failed to export $target: ${Assimp.aiGetErrorString()}"
            }
            rewriteTextureUris(target)
            Files.writeString(versionFile, CACHE_VERSION)
        } finally {
            Assimp.aiReleaseImport(scene)
        }
        return target
    }

    private fun rewriteTextureUris(target: Path) {
        val bytes = Files.readAllBytes(target)
        val glb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        require(glb.int == 0x46546C67) { "Not a binary glTF file: $target" }
        require(glb.int == 2) { "Unsupported binary glTF version: $target" }
        glb.int // total byte length

        val jsonLength = glb.int
        require(glb.int == GLTF_JSON_CHUNK) { "Binary glTF has no leading JSON chunk: $target" }
        val jsonStart = glb.position()
        val originalJson = bytes.decodeToString(jsonStart, jsonStart + jsonLength)
        val uriPattern = Regex("""("uri"\s*:\s*")([^"]+)(")""")
        val rewrittenUris = uriPattern.replace(originalJson) { match ->
            val originalUri = match.groupValues[2]
            val fileName = originalUri.substringAfterLast("\\\\").substringAfterLast('/')
            if (fileName == originalUri) {
                match.value
            } else {
                "${match.groupValues[1]}../texture/$fileName${match.groupValues[3]}"
            }
        }
        // Assimp exports constant white FBX vertex colors as COLOR_0. Kool gives
        // vertex colors priority over a material's base-color texture, which makes
        // the face, body and several costume meshes render white. Dropping the
        // unused attribute makes Kool sample the correctly assigned PNG instead.
        val rewrittenJson = rewrittenUris.replace(
            Regex(""",\s*"COLOR_0"\s*:\s*\d+"""),
            "",
        )

        val rewritten = rewrittenJson.encodeToByteArray()
        require(rewritten.size <= jsonLength) { "Rewritten glTF JSON grew unexpectedly" }
        bytes.fill(' '.code.toByte(), jsonStart, jsonStart + jsonLength)
        rewritten.copyInto(bytes, jsonStart)
        Files.write(target, bytes)
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val source = Path.of(args.getOrElse(0) { "run/model/fbx/Neru.fbx" })
        val target = Path.of(args.getOrElse(1) { "run/model/cache/Neru.glb" })
        println(convertIfNeeded(source, target).toAbsolutePath())
    }
}
