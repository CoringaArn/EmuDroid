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
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@OptIn(DelicateCoroutinesApi::class)
class MainActivity : RetrogradeComponentActivity(), BusyActivity {
    @Inject
    lateinit var gameInteractor: GameInteractor

    @Inject
    lateinit var retrogradeDb: RetrogradeDatabase

    // CONFIGURACAO DO JOGO
    private val gameFile = File("/sdcard/Roms/PSP/gta.iso")
    private val gameTitle = "GTA: Liberty City Stories"
    private val gameSystemId = SystemID.PSP.dbname

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Tela preta (esconde a UI)
        setContentView(View(this))

        GlobalScope.launch {
            // 1. Verifica se a ROM existe
            if (!gameFile.exists()) {
                // ROM nao existe - fecha o app
                runOnUiThread { finish() }
                return@launch
            }

            // 2. Busca no banco
            val existingGame = retrogradeDb.gameDao().selectByFileUri(gameFile.path)

            // 3. Cria o Game (se nao existir)
            val game =
                existingGame ?: Game(
                    fileName = gameFile.name,
                    fileUri = gameFile.path,
                    title = gameTitle,
                    systemId = gameSystemId,
                    developer = null,
                    coverFrontUrl = null,
                    lastIndexedAt = System.currentTimeMillis(),
                ).also {
                    retrogradeDb.gameDao().insert(listOf(it))
                }

            // 4. Roda o jogo
            runOnUiThread {
                gameInteractor.onGamePlay(game)
            }
        }
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
