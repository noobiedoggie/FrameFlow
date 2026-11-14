package com.frameflow.studio.data

import android.content.Context
import androidx.core.content.edit
import com.frameflow.studio.ai.GeminiClient
import com.frameflow.studio.ai.LocalAnimationSynthesizer
import com.frameflow.studio.model.AnimationProject
import com.frameflow.studio.model.AspectRatioPreset
import com.frameflow.studio.model.ProjectSummary
import com.frameflow.studio.model.toSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

interface ProjectRepository {
    val summaries: Flow<List<ProjectSummary>>
    suspend fun refresh()
    suspend fun loadProject(id: String): AnimationProject?
    suspend fun saveProject(project: AnimationProject)
    suspend fun deleteProject(id: String)
    suspend fun duplicateProject(id: String): AnimationProject?
}

class FileProjectRepository(
    private val rootDir: File,
) : ProjectRepository {

    constructor(context: Context) : this(File(context.filesDir, "projects"))

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val mutex = Mutex()
    private val _summaries = MutableStateFlow<List<ProjectSummary>>(emptyList())
    override val summaries: Flow<List<ProjectSummary>> = _summaries.asStateFlow()

    private val seedMarker: File
        get() = File(rootDir, ".seeded_v1")

    override suspend fun refresh() {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                ensureSeededLocked()
                _summaries.value = readAllSummariesLocked()
            }
        }
    }

    override suspend fun loadProject(id: String): AnimationProject? = withContext(Dispatchers.IO) {
        mutex.withLock {
            ensureSeededLocked()
            val file = projectFile(id)
            if (!file.exists()) return@withLock null
            runCatching {
                json.decodeFromString<AnimationProject>(file.readText(Charsets.UTF_8))
            }.getOrNull()
        }
    }

    override suspend fun saveProject(project: AnimationProject) {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                if (!rootDir.exists()) rootDir.mkdirs()
                val updated = project.copy(updatedAt = System.currentTimeMillis())
                writeProjectLocked(updated)
                _summaries.value = readAllSummariesLocked()
            }
        }
    }

    override suspend fun deleteProject(id: String) {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val file = projectFile(id)
                if (file.exists()) file.delete()
                _summaries.value = readAllSummariesLocked()
            }
        }
    }

    override suspend fun duplicateProject(id: String): AnimationProject? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val file = projectFile(id)
            if (!file.exists()) return@withLock null
            val original = runCatching {
                json.decodeFromString<AnimationProject>(file.readText(Charsets.UTF_8))
            }.getOrNull() ?: return@withLock null

            val copy = original.copy(
                id = com.frameflow.studio.model.newId(),
                title = "${original.title} Copy",
                updatedAt = System.currentTimeMillis(),
            )
            writeProjectLocked(copy)
            _summaries.value = readAllSummariesLocked()
            copy
        }
    }

    private fun projectFile(id: String): File {
        val safeId = id.replace(Regex("[^A-Za-z0-9_-]"), "_")
        return File(rootDir, "$safeId.json")
    }

    private fun ensureSeededLocked() {
        if (!rootDir.exists()) rootDir.mkdirs()
        if (seedMarker.exists()) return

        val existing = rootDir.listFiles { f -> f.extension == "json" }?.toList().orEmpty()
        if (existing.isEmpty()) {
            StarterProjects.createSamples().forEach { writeProjectLocked(it) }
        }
        seedMarker.writeText("1", Charsets.UTF_8)
    }

    private fun writeProjectLocked(project: AnimationProject) {
        val target = projectFile(project.id)
        val tmp = File(rootDir, "${target.name}.tmp")
        tmp.writeText(json.encodeToString(AnimationProject.serializer(), project), Charsets.UTF_8)
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
    }

    private fun readAllSummariesLocked(): List<ProjectSummary> {
        if (!rootDir.exists()) return emptyList()
        return rootDir.listFiles { f -> f.extension == "json" }
            ?.mapNotNull { file ->
                runCatching {
                    json.decodeFromString<AnimationProject>(file.readText(Charsets.UTF_8)).toSummary()
                }.getOrNull()
            }
            ?.sortedByDescending { it.updatedAt }
            ?: emptyList()
    }
}

object StarterProjects {
    fun createSamples(): List<AnimationProject> {
        val layers = AnimationProject.defaultLayers()
        val colorId = layers[0].id
        val inkId = layers[1].id

        val bounce = LocalAnimationSynthesizer.synthesize(
            prompt = "Bouncing indigo ball with squash and stretch",
            aspectRatio = AspectRatioPreset.PORTRAIT_9_16,
            inkLayerId = inkId,
            colorLayerId = colorId,
            frameCount = 12,
        )

        val wave = LocalAnimationSynthesizer.synthesize(
            prompt = "Friendly stick character waving hello",
            aspectRatio = AspectRatioPreset.SQUARE_1_1,
            inkLayerId = inkId,
            colorLayerId = colorId,
            frameCount = 10,
        )

        val now = System.currentTimeMillis()
        return listOf(
            AnimationProject(
                id = "starter_bounce",
                title = "Squash & Bounce",
                fps = bounce.suggestedFps,
                aspectRatio = AspectRatioPreset.PORTRAIT_9_16,
                backgroundColorArgb = 0xFFFAF9F6,
                layers = layers,
                frames = bounce.frames,
                updatedAt = now,
            ),
            AnimationProject(
                id = "starter_wave",
                title = "Friendly Wave",
                fps = wave.suggestedFps,
                aspectRatio = AspectRatioPreset.SQUARE_1_1,
                backgroundColorArgb = 0xFFF4F6FB,
                layers = layers,
                frames = wave.frames,
                updatedAt = now - 1000L,
            ),
        )
    }
}

data class AiSettings(
    val apiKey: String = "",
    val model: String = GeminiClient.DEFAULT_MODEL,
)

class AiSettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("frameflow_ai_prefs", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(
        AiSettings(
            apiKey = prefs.getString(KEY_API_KEY, "") ?: "",
            model = normalizeModel(prefs.getString(KEY_MODEL, GeminiClient.DEFAULT_MODEL)),
        )
    )
    val settings: StateFlow<AiSettings> = _settings.asStateFlow()

    fun getGeminiApiKey(): String = prefs.getString(KEY_API_KEY, "") ?: ""

    fun setGeminiApiKey(key: String) {
        val clean = key.trim()
        prefs.edit { putString(KEY_API_KEY, clean) }
        _settings.value = _settings.value.copy(apiKey = clean)
    }

    fun getGeminiModel(): String = normalizeModel(prefs.getString(KEY_MODEL, GeminiClient.DEFAULT_MODEL))

    fun setGeminiModel(model: String) {
        val normalized = normalizeModel(model)
        prefs.edit { putString(KEY_MODEL, normalized) }
        _settings.value = _settings.value.copy(model = normalized)
    }

    fun updateSettings(apiKey: String, model: String) {
        val cleanKey = apiKey.trim()
        val normalized = normalizeModel(model)
        prefs.edit {
            putString(KEY_API_KEY, cleanKey)
            putString(KEY_MODEL, normalized)
        }
        _settings.value = AiSettings(apiKey = cleanKey, model = normalized)
    }

    private fun normalizeModel(raw: String?): String = normalizeModelId(raw)

    companion object {
        private const val KEY_API_KEY = "gemini_api_key"
        private const val KEY_MODEL = "gemini_model"
        private const val LEGACY_MODEL_ID = "gemini-4-hydrogen"
        val AVAILABLE_MODELS = listOf(
            "gemini-3.8-flash" to "Gemini 3.8 Flash (Fast & Balanced)",
            "gemini-4-argon" to "Gemini 4 Argon (Frontier Reasoning)",
            "gemini-3.6-flash" to "Gemini 3.6 Flash",
            "gemini-3.5-flash-lite" to "Gemini 3.5 Flash-Lite (Ultra Fast)",
        )

        fun normalizeModelId(raw: String?): String {
            val trimmed = raw?.trim().orEmpty()
            if (trimmed.isEmpty() || trimmed.equals(LEGACY_MODEL_ID, ignoreCase = true)) {
                return GeminiClient.DEFAULT_MODEL
            }
            return AVAILABLE_MODELS.firstOrNull { it.first.equals(trimmed, ignoreCase = true) }?.first
                ?: trimmed
        }
    }
}
