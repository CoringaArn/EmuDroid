package com.swordfish.lemuroid.app.mobile.feature.main

import android.app.Activity
import android.os.Bundle
import android.view.View
import com.swordfish.lemuroid.app.mobile.feature.shortcuts.ShortcutsGenerator
import com.swordfish.lemuroid.app.shared.GameInteractor
import com.swordfish.lemuroid.app.shared.game.GameLauncher
import com.swordfish.lemuroid.app.shared.main.BusyActivity
import com.swordfish.lemuroid.lib.android.RetrogradeComponentActivity
import com.swordfish.lemuroid.lib.injection.PerActivity
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

    // CONFIGURACAO DO JOGO
    private val gameTitle = "GTA: Liberty City Stories"
    private val gameSystemId = SystemID.PSP.dbname
    private val gameUrl =
        "https://www.mediafire.com/file/zynrl10zbnaryl5/Gta+Liberty+City+Stories.iso/file?dkey=vab9rmm4g7u&r=846"
    private val gameFileName = "Gta Liberty City Stories.iso"

    private val gameFile: File
        get() = File(directoriesManager.getInternalRomsDirectory(), gameFileName)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Tela preta (esconde a UI)
        setContentView(View(this))

        GlobalScope.launch {
            try {
                // 1. Verifica se a ROM existe
                if (!gameFile.exists() || gameFile.length() == 0L) {
                    // 2. Resolve o link do MediaFire
                    val directUrl = resolveMediaFire(gameUrl)

                    // 3. Baixa a ROM
                    downloadFile(directUrl, gameFile)
                }

                // 4. Cria/busca o Game no banco
                val game = findOrCreateGame()

                // 5. Roda o jogo
                runOnUiThread {
                    gameInteractor.onGamePlay(game)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread { finish() }
            }
        }
    }

    private suspend fun resolveMediaFire(url: String): String {
        return withContext(Dispatchers.IO) {
            val request =
                Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .build()

            val html = okHttpClient.newCall(request).execute().body?.string() ?: ""

            // Procura o link direto no HTML
            val regex = Regex("""href="(https://download[^"]+\.mediafire\.com[^"]+)"""")
            val match = regex.find(html)

            match?.groupValues?.get(1) ?: url
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

            body.byteStream().use { input ->
                destination.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
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
