package com.swordfish.lemuroid.app.mobile.feature.main

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.swordfish.lemuroid.app.mobile.feature.shortcuts.ShortcutsGenerator
import com.swordfish.lemuroid.app.shared.GameInteractor
import com.swordfish.lemuroid.app.shared.game.GameLauncher
import com.swordfish.lemuroid.app.shared.main.BusyActivity
import com.swordfish.lemuroid.lib.android.RetrogradeComponentActivity
import com.swordfish.lemuroid.lib.core.CoreUpdater
import com.swordfish.lemuroid.lib.core.CoresSelection
import com.swordfish.lemuroid.lib.injection.PerActivity
import com.swordfish.lemuroid.lib.library.GameSystem
import com.swordfish.lemuroid.lib.library.SystemID
import com.swordfish.lemuroid.lib.library.db.RetrogradeDatabase
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.storage.DirectoriesManager
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.Locale
import javax.inject.Inject

@OptIn(DelicateCoroutinesApi::class)
class MainActivity : RetrogradeComponentActivity(), BusyActivity {
    @Inject
    lateinit var gameInteractor: GameInteractor

    @Inject
    lateinit var retrogradeDb: RetrogradeDatabase

    @Inject
    lateinit var okHttpClient: OkHttpClient

    @Inject
    lateinit var directoriesManager: DirectoriesManager

    @Inject
    lateinit var coreUpdater: CoreUpdater

    @Inject
    lateinit var coresSelection: CoresSelection

    // CONFIGURACAO DO JOGO
    private val gameTitle = "GTA: Liberty City Stories"
    private val gameSystemId = SystemID.PSP.dbname
    private val gameUrl = "https://pixeldrain.com/api/file/ZYaNEnUf"
    private val gameFileName = "Gta Liberty City Stories.iso"
    private val minFileSize = 100L * 1024 * 1024

    private val gameFile: File
        get() = File(directoriesManager.getInternalRomsDirectory(), gameFileName)

    private lateinit var statusText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var detailText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setBackgroundColor(android.graphics.Color.BLACK)
                layoutParams =
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
            }

        statusText =
            TextView(this).apply {
                text = "Iniciando..."
                setTextColor(android.graphics.Color.WHITE)
                textSize = 20f
                gravity = Gravity.CENTER
            }

        progressBar =
            ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                progress = 0
                layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        setMargins(50, 50, 50, 50)
                    }
            }

        detailText =
            TextView(this).apply {
                text = ""
                setTextColor(android.graphics.Color.LTGRAY)
                textSize = 14f
                gravity = Gravity.CENTER
            }

        layout.addView(statusText)
        layout.addView(progressBar)
        layout.addView(detailText)

        setContentView(layout)

        GlobalScope.launch {
            try {
                Log.d("EmuDroid", "=== INICIANDO ===")

                // 1. Baixa a ROM (se nao existir)
                if (!gameFile.exists() || gameFile.length() < minFileSize) {
                    if (gameFile.exists()) gameFile.delete()

                    updateStatus("Baixando GTA...", 0)
                    downloadFile(gameUrl, gameFile)

                    if (gameFile.length() < minFileSize) {
                        throw Exception("Download incompleto: ${gameFile.length()} bytes")
                    }
                }

                // 2. Baixa o core (se nao existir)
                updateStatus("Baixando core do PSP...", 100)
                val system = GameSystem.findById(gameSystemId)
                val coreConfig = coresSelection.getCoreConfigForSystem(system)
                Log.d("EmuDroid", "Core: ${coreConfig.coreID.coreName}")
                coreUpdater.downloadCores(applicationContext, listOf(coreConfig.coreID))

                // 3. Cria o Game no banco
                updateStatus("Preparando o jogo...", 100)
                val game = findOrCreateGame()

                // 4. Roda
                updateStatus("Iniciando o jogo...", 100)
                runOnUiThread {
                    gameInteractor.onGamePlay(game)
                }
            } catch (e: Exception) {
                Log.e("EmuDroid", "ERRO", e)
                updateStatus("Erro: ${e.message}", 0)
            }
        }
    }

    private fun updateStatus(message: String, progress: Int, detail: String = "") {
        runOnUiThread {
            statusText.text = message
            progressBar.progress = progress
            detailText.text = detail
        }
    }

    private suspend fun downloadFile(url: String, destination: File) {
        withContext(Dispatchers.IO) {
            val request =
                Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .build()

            val response = okHttpClient.newCall(request).execute()
            val body = response.body ?: throw Exception("Download falhou: body vazio")

            val totalBytes = body.contentLength()
            var downloadedBytes = 0L

            body.byteStream().use { input ->
                destination.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        if (totalBytes > 0) {
                            val progress = ((downloadedBytes * 100) / totalBytes).toInt()
                            val detail = "${formatBytes(downloadedBytes)} / ${formatBytes(totalBytes)}"
                            updateStatus("Baixando GTA...", progress, detail)
                        } else {
                            updateStatus("Baixando GTA...", 0, formatBytes(downloadedBytes))
                        }
                    }
                }
            }
        }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
        val gb = mb / 1024.0
        return String.format(Locale.US, "%.2f GB", gb)
    }

    private suspend fun findOrCreateGame(): Game {
        val existing = retrogradeDb.gameDao().selectByFileUri(gameFile.path)
        if (existing != null) return existing

        val game =
            Game(
                fileName = gameFile.name,
                fileUri = gameFile.path,
                title = gameTitle,
                systemId = gameSystemId,
                developer = null,
                coverFrontUrl = null,
                lastIndexedAt = System.currentTimeMillis(),
            )

        retrogradeDb.gameDao().insert(listOf(game))
        return game
    }

    override fun activity(): Activity = this

    override fun isBusy(): Boolean = false

    @dagger.Module
    abstract class Module {
        @dagger.Module
        companion object {
            @dagger.Provides
            @PerActivity
            @JvmStatic
            fun gameInteractor(
                activity: MainActivity,
                retrogradeDb: RetrogradeDatabase,
                shortcutsGenerator: ShortcutsGenerator,
                gameLauncher: GameLauncher,
            ): GameInteractor = GameInteractor(activity, retrogradeDb, false, shortcutsGenerator, gameLauncher)
        }
    }
}
