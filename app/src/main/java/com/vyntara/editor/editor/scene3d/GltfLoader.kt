package com.vyntara.editor.editor.scene3d

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Subset glTF 2.0 JSON loader (not full GLB binary yet).
 * Supports: meshes with POSITION float3 accessors, TRIANGLES mode,
 * single buffer / bufferView, node transforms (translation/scale).
 * Returns Object3D list + optional MeshData for custom draw.
 */
data class MeshData(
    val name: String,
    val positions: FloatArray, // x,y,z interleaved
    val indices: IntArray?,
    val translation: Vec3 = Vec3(),
    val scale: Vec3 = Vec3(1f, 1f, 1f)
)

data class GltfLoadResult(
    val meshes: List<MeshData>,
    val objects: List<Object3D>,
    val warnings: List<String>
)

object GltfLoader {

    fun loadJson(context: Context, uri: Uri): GltfLoadResult = loadGltfOrGlb(context, uri)

    fun loadGltfOrGlb(context: Context, uri: Uri): GltfLoadResult {
        val warnings = mutableListOf<String>()
        return try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return GltfLoadResult(emptyList(), emptyList(), listOf("Cannot read URI"))
            if (bytes.size >= 12 &&
                bytes[0] == 'g'.code.toByte() &&
                bytes[1] == 'l'.code.toByte() &&
                bytes[2] == 'T'.code.toByte() &&
                bytes[3] == 'F'.code.toByte()
            ) {
                parseGlb(bytes, warnings)
            } else {
                val text = bytes.toString(Charsets.UTF_8)
                parseGltfJson(text, warnings)
            }
        } catch (e: Exception) {
            GltfLoadResult(emptyList(), emptyList(), listOf(e.message ?: "glTF/GLB load failed"))
        }
    }

    private fun parseGlb(bytes: ByteArray, warnings: MutableList<String>): GltfLoadResult {
        val bb = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        val magic = bb.int
        val version = bb.int
        val length = bb.int
        if (magic != 0x46546C67) return GltfLoadResult(emptyList(), emptyList(), listOf("Invalid GLB magic"))
        if (version != 2) warnings.add("glTF version $version (expected 2)")
        var jsonStr: String? = null
        var binChunk: ByteArray? = null
        var offset = 12
        while (offset + 8 <= bytes.size) {
            bb.position(offset)
            val chunkLen = bb.int
            val chunkType = bb.int
            offset += 8
            if (offset + chunkLen > bytes.size) break
            val chunk = bytes.copyOfRange(offset, offset + chunkLen)
            offset += chunkLen
            // align to 4
            while (offset % 4 != 0 && offset < bytes.size) offset++
            when (chunkType) {
                0x4E4F534A -> jsonStr = chunk.toString(Charsets.UTF_8) // JSON
                0x004E4942 -> binChunk = chunk // BIN
            }
        }
        if (jsonStr == null) return GltfLoadResult(emptyList(), emptyList(), listOf("GLB missing JSON chunk"))
        return parseGltfJson(jsonStr, warnings, externalBin = binChunk)
    }

    private fun parseGltfJson(text: String, warnings: MutableList<String>, externalBin: ByteArray? = null): GltfLoadResult {
        val root = JSONObject(text)
        val accessors = root.optJSONArray("accessors") ?: JSONArray()
        val bufferViews = root.optJSONArray("bufferViews") ?: JSONArray()
        val buffers = root.optJSONArray("buffers") ?: JSONArray()
        val meshes = root.optJSONArray("meshes") ?: JSONArray()
        val nodes = root.optJSONArray("nodes") ?: JSONArray()

        // Only support data URI buffers in this subset
        val bufferBytes = Array<ByteArray?>(buffers.length()) { null }
        for (i in 0 until buffers.length()) {
            val b = buffers.getJSONObject(i)
            val uri = b.optString("uri", "")
            when {
                uri.startsWith("data:") -> {
                    val b64 = uri.substringAfter("base64,", "")
                    if (b64.isNotEmpty()) {
                        bufferBytes[i] = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                    } else warnings.add("Buffer $i: unsupported data URI")
                }
                uri.isNotEmpty() -> warnings.add("External buffer URI not loaded: $uri")
                externalBin != null && i == 0 -> bufferBytes[i] = externalBin
                else -> warnings.add("Buffer $i empty and no GLB BIN")
            }
        }

        fun readAccessor(index: Int): FloatArray? {
            if (index < 0 || index >= accessors.length()) return null
            val acc = accessors.getJSONObject(index)
            val type = acc.optString("type")
            val compType = acc.optInt("componentType")
            val count = acc.optInt("count")
            val viewIdx = acc.optInt("bufferView", -1)
            if (viewIdx < 0 || viewIdx >= bufferViews.length()) return null
            val view = bufferViews.getJSONObject(viewIdx)
            val bufIdx = view.optInt("buffer")
            val bytes = bufferBytes.getOrNull(bufIdx) ?: return null
            val offset = view.optInt("byteOffset", 0) + acc.optInt("byteOffset", 0)
            val stride = view.optInt("byteStride", 0)
            if (type != "VEC3" || compType != 5126) {
                warnings.add("Accessor $index: only FLOAT VEC3 supported")
                return null
            }
            val floats = FloatArray(count * 3)
            val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val elemSize = if (stride > 0) stride else 12
            for (i in 0 until count) {
                val pos = offset + i * elemSize
                floats[i * 3] = bb.getFloat(pos)
                floats[i * 3 + 1] = bb.getFloat(pos + 4)
                floats[i * 3 + 2] = bb.getFloat(pos + 8)
            }
            return floats
        }

        fun readIndices(index: Int): IntArray? {
            if (index < 0 || index >= accessors.length()) return null
            val acc = accessors.getJSONObject(index)
            val compType = acc.optInt("componentType")
            val count = acc.optInt("count")
            val viewIdx = acc.optInt("bufferView", -1)
            if (viewIdx < 0) return null
            val view = bufferViews.getJSONObject(viewIdx)
            val bytes = bufferBytes.getOrNull(view.optInt("buffer")) ?: return null
            val offset = view.optInt("byteOffset", 0) + acc.optInt("byteOffset", 0)
            val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val out = IntArray(count)
            when (compType) {
                5123 -> { // UNSIGNED_SHORT
                    for (i in 0 until count) out[i] = bb.getShort(offset + i * 2).toInt() and 0xFFFF
                }
                5125 -> { // UNSIGNED_INT
                    for (i in 0 until count) out[i] = bb.getInt(offset + i * 4)
                }
                else -> {
                    warnings.add("Index componentType $compType unsupported")
                    return null
                }
            }
            return out
        }

        val meshDataList = mutableListOf<MeshData>()
        val objects = mutableListOf<Object3D>()

        for (mi in 0 until meshes.length()) {
            val mesh = meshes.getJSONObject(mi)
            val name = mesh.optString("name", "Mesh$mi")
            val prims = mesh.optJSONArray("primitives") ?: continue
            for (pi in 0 until prims.length()) {
                val prim = prims.getJSONObject(pi)
                val attrs = prim.optJSONObject("attributes") ?: continue
                val posAcc = attrs.optInt("POSITION", -1)
                val positions = readAccessor(posAcc) ?: continue
                val indices = if (prim.has("indices")) readIndices(prim.optInt("indices")) else null
                meshDataList.add(MeshData(name, positions, indices))
                objects.add(
                    Object3D(
                        name = name,
                        type = Object3DType.CUBE, // drawn via mesh path when available
                        colorR = 0.7f, colorG = 0.75f, colorB = 0.9f
                    )
                )
            }
        }

        // Apply node translation/scale if mesh index present
        for (ni in 0 until nodes.length()) {
            val node = nodes.getJSONObject(ni)
            if (!node.has("mesh")) continue
            val meshIndex = node.optInt("mesh")
            if (meshIndex !in objects.indices) continue
            val tArr = node.optJSONArray("translation")
            val sArr = node.optJSONArray("scale")
            var obj = objects[meshIndex]
            if (tArr != null && tArr.length() >= 3) {
                obj = obj.copy(
                    position = Vec3(
                        tArr.getDouble(0).toFloat(),
                        tArr.getDouble(1).toFloat(),
                        tArr.getDouble(2).toFloat()
                    )
                )
            }
            if (sArr != null && sArr.length() >= 3) {
                obj = obj.copy(
                    scale = Vec3(
                        sArr.getDouble(0).toFloat(),
                        sArr.getDouble(1).toFloat(),
                        sArr.getDouble(2).toFloat()
                    )
                )
            }
            objects[meshIndex] = obj
            if (meshIndex in meshDataList.indices) {
                meshDataList[meshIndex] = meshDataList[meshIndex].copy(
                    translation = obj.position,
                    scale = obj.scale
                )
            }
        }

        if (objects.isEmpty()) warnings.add("No mesh primitives with POSITION found")
        return GltfLoadResult(meshDataList, objects, warnings)
    }
}
