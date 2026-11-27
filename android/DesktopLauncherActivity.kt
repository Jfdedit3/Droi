package com.droiddeck.launcher

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.droiddeck.launcher.ui.DroidDeckTheme
import com.droiddeck.launcher.ui.MenuItem
import com.droiddeck.launcher.ui.SettingsGroup
import com.droiddeck.launcher.ui.TabStrip
import android.content.Intent
import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import com.droiddeck.launcher.files.AllFilesAccess
import java.io.File
import com.droiddeck.launcher.runtime.LinuxRuntime
import com.droiddeck.launcher.session.ComponentsManager
import com.droiddeck.launcher.session.SessionState
import android.os.Handler
import android.os.Looper
import com.droiddeck.launcher.core.AddonPack
import com.droiddeck.launcher.gpu.SystemVulkanDriver
import android.os.Bundle
import android.widget.Toast
import com.droiddeck.launcher.session.SessionService

internal enum class DroiDesktop(val id: String, val label: String, val description: String,
                                val executables: List<String>, val packages: List<String>) {
    DROI("droi", "Droi Plasma", "KDE Plasma personnalisé · Wayland",
        listOf("openbox", "Xwayland", "xdpyinfo", "xauth", "mcookie", "flock", "xrdb", "xprop", "xterm"),
        listOf("openbox", "python3-gi", "python3-gi-cairo", "gir1.2-gtk-3.0", "gir1.2-wnck-3.0",
            "x11-utils", "x11-xserver-utils", "xterm", "xwayland", "xauth", "util-linux"));

    fun installed(context: Context): Boolean {
        val root = LinuxRuntime.rootDir(context)
        val gi = File(root, "usr/lib/python3/dist-packages/gi")
        val typelibs = File(root, "usr/lib/aarch64-linux-gnu/girepository-1.0")
        return executables.all { File(root, "usr/bin/$it").isFile } &&
            gi.isDirectory && listOf("Gtk-3.0.typelib", "Wnck-3.0.typelib").all { File(typelibs, it).isFile } &&
            File(root, "usr/lib/python3/dist-packages/cairo").isDirectory
    }

    companion object {
        val scales = listOf(100, 125, 150, 200)
        fun scale(context: Context): Int =
            context.getSharedPreferences("droi-desktop", Context.MODE_PRIVATE)
                .getInt("scale", 150).takeIf { it in scales } ?: 150

        fun selectScale(context: Context, scale: Int) {
            if (scale in scales) context.getSharedPreferences("droi-desktop", Context.MODE_PRIVATE)
                .edit().putInt("scale", scale).apply()
        }

        fun selected(context: Context): DroiDesktop = DROI

    }
}

class DesktopLauncherActivity : ComponentActivity() {
    private var storageGranted by mutableStateOf(false)
    private var storagePrompt by mutableStateOf(false)

    private fun hasSharedStorage(): Boolean =
        if (Build.VERSION.SDK_INT >= 30) !AllFilesAccess.missing()
        else checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    override fun onResume() {
        super.onResume()
        storageGranted = hasSharedStorage()
    }

    private fun requestSharedStorage() {
        if (Build.VERSION.SDK_INT >= 30) {
            AllFilesAccess.open(this)
        } else {
            requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE), 714)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 714) storageGranted = hasSharedStorage()
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val components by lazy { ComponentsMenu(this, mainHandler) }
    private val protons by lazy { ProtonMenu(this, mainHandler, onInstalled = { components.refreshComponents() }) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DroidDeckTheme {
                var tab by rememberSaveable { mutableIntStateOf(0) }
                var desktopChoice by rememberSaveable { mutableStateOf(false) }
                var prelaunch by remember { mutableStateOf(false) }
                val updateWorking by DroiUpdates.working.collectAsState()
                val updateMessage by DroiUpdates.status.collectAsState()
                val updateReady by DroiUpdates.ready.collectAsState()
                val updateAvailable by DroiUpdates.available.collectAsState()
                LaunchedEffect(prelaunch, updateWorking, updateMessage) {
                    if (prelaunch && !updateWorking && (updateMessage == "Droi est à jour." || updateMessage == "Aucune version Droi disponible sur GitHub.")) {
                        prelaunch = false
                        launchUbuntu()
                    }
                }
                var desktopScale by rememberSaveable { mutableIntStateOf(DroiDesktop.scale(this@DesktopLauncherActivity)) }
                var automatic by remember { mutableStateOf(DroiUpdates.isEnabled(this)) }
                androidx.activity.compose.BackHandler(enabled = tab != 0) { tab = 0 }
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Droi Desktop", style = MaterialTheme.typography.headlineMedium)
                        if (storagePrompt) {
                            AlertDialog(
                                onDismissRequest = { storagePrompt = false },
                                title = { Text("Accès au stockage Android") },
                                text = { Text("Pour afficher tes fichiers dans /sdcard et Storage depuis Ubuntu, Android doit autoriser Droi à accéder aux fichiers partagés. Tu peux refuser et continuer avec le stockage privé Ubuntu.") },
                                confirmButton = {
                                    Button(onClick = { storagePrompt = false; requestSharedStorage() }) {
                                        Text("Autoriser dans Android")
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { storagePrompt = false; startUbuntuSession() }) {
                                        Text("Continuer sans accès")
                                    }
                                }
                            )
                        }
                        TabStrip(listOf("Bureaux", "Composants", "Paramètres"), tab, { tab = it })
                        if (tab == 0) {
                            SettingsGroup("Bureaux Linux") {
                                MenuItem("Ubuntu", false, detail = "Droi Plasma") {
                                    if (SessionState.running) launchUbuntu() else desktopChoice = true
                                }
                                Text("Droi Plasma : bureau KDE, menu Démarrer, applications épinglées et barre centrée.")
                                if (desktopChoice) {
                                    AlertDialog(
                                        onDismissRequest = { desktopChoice = false },
                                        title = { Text("Lancer Droi Plasma") },
                                        text = {
                                            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                                                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                run {
                                                    Text("Taille de l’interface", style = MaterialTheme.typography.titleMedium)
                                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                        DroiDesktop.scales.forEach { scale ->
                                                            FilterChip(selected = desktopScale == scale,
                                                                onClick = { desktopScale = scale },
                                                                label = { Text("$scale %") })
                                                        }
                                                    }
                                                    Text("150 % recommandé sur téléphone. La taille est adaptée au bureau.",
                                                        style = MaterialTheme.typography.bodySmall)
                                                }
                                                Text(if (DroiDesktop.DROI.installed(this@DesktopLauncherActivity))
                                                    "Bureau prêt" else "Installation des composants au premier lancement")
                                                Text("Tes fichiers, applications et préfixes Wine sont conservés.", style = MaterialTheme.typography.bodySmall)
                                            }
                                        },
                                        confirmButton = {
                                            Button(onClick = {
                                                if (!SessionState.running && SessionState.installing == null) {
                                                    DroiDesktop.selectScale(this@DesktopLauncherActivity, desktopScale)
                                                    desktopChoice = false
                                                    prelaunch = true
                                                    DroiUpdates.check(this@DesktopLauncherActivity, force = true)
                                                } else {
                                                    Toast.makeText(this@DesktopLauncherActivity, "Une session ou installation est déjà en cours.", Toast.LENGTH_SHORT).show()
                                                }
                                            }) { Text("Lancer Ubuntu") }
                                        },
                                        dismissButton = { TextButton(onClick = { desktopChoice = false }) { Text("Annuler") } },
                                    )
                                }
                            }
                            if (prelaunch) {
                                AlertDialog(
                                    onDismissRequest = { if (!updateWorking) prelaunch = false },
                                    title = { Text("Mises à jour avant démarrage") },
                                    text = {
                                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Text(updateMessage.ifEmpty { "Vérification de Droi…" })
                                            if (updateWorking) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                            if (!updateWorking && updateReady > 0 && updateReady == updateAvailable)
                                                Text("Android doit confirmer l’installation de l’APK signé.")
                                        }
                                    },
                                    confirmButton = {
                                        if (!updateWorking && updateReady > 0 && updateReady == updateAvailable) {
                                            Button(onClick = { DroiUpdates.install(this@DesktopLauncherActivity) }) { Text("Installer") }
                                        } else if (!updateWorking && updateAvailable > 0) {
                                            Button(onClick = { DroiUpdates.check(this@DesktopLauncherActivity, force = true, manual = true) }) { Text("Télécharger") }
                                        } else if (!updateWorking) {
                                            Button(onClick = { DroiUpdates.check(this@DesktopLauncherActivity, force = true) }) { Text("Réessayer") }
                                        }
                                    },
                                    dismissButton = {
                                        if (!updateWorking) TextButton(onClick = { prelaunch = false; launchUbuntu() }) { Text("Continuer sans mise à jour") }
                                    }
                                )
                            }
                        } else if (tab == 1) {
                            DesktopComponents()
                        } else {
                            SettingsGroup("Stockage Android") {
                                Text(if (storageGranted) "Accès aux fichiers partagés autorisé" else "Accès aux fichiers partagés non autorisé : certains dossiers peuvent sembler vides.")
                                MenuItem("Gérer l’accès au stockage", storageGranted) { requestSharedStorage() }
                                Text("Après avoir changé l’autorisation, ferme puis relance Ubuntu pour actualiser les montages.", style = MaterialTheme.typography.bodySmall)
                            }
                            XclipseSettings()
                            SettingsGroup("Mises à jour de Droi") {
                                Text("Version installée : ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                                MenuItem("Télécharger automatiquement les nouveaux APK", automatic) {
                                    automatic = !automatic
                                    DroiUpdates.setEnabled(this@DesktopLauncherActivity, automatic)
                                    if (automatic) DroiUpdates.check(this@DesktopLauncherActivity, force = true, report = ::showUpdateStatus)
                                }
                                val message by DroiUpdates.status.collectAsState()
                                val ready by DroiUpdates.ready.collectAsState()
                                val available by DroiUpdates.available.collectAsState()
                                if (message.isNotEmpty()) Text(message)
                                if (ready > 0) MenuItem("Installer la mise à jour", false) {
                                    DroiUpdates.install(this@DesktopLauncherActivity)
                                }
                                if (available > 0 && ready == 0) MenuItem("Télécharger la version $available", false) {
                                    DroiUpdates.check(this@DesktopLauncherActivity, force = true, manual = true, report = ::showUpdateStatus)
                                }
                                MenuItem("Vérifier maintenant", false) {
                                    DroiUpdates.check(this@DesktopLauncherActivity, force = true, manual = true, report = ::showUpdateStatus)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun XclipseSettings() {
        if (!remember { SystemVulkanDriver.isXclipse() }) return
        val pack = AddonPack.POWERVR
        val state by pack.state.collectAsState()
        LaunchedEffect(Unit) { pack.refresh(this@DesktopLauncherActivity) }
        SettingsGroup("Pilote Vulkan Samsung Xclipse") {
            Text("Ce module permet à Ubuntu d’utiliser le pilote Vulkan Samsung du téléphone.")
            when (val current = state) {
                AddonPack.State.Ready -> Text("Module installé · disponible au lancement avec le pilote système")
                is AddonPack.State.Downloading -> Text("Installation du module · ${(current.progress * 100).toInt()} %")
                else -> {
                    if (current == AddonPack.State.Failed) Text(pack.failure ?: "Le téléchargement a échoué.")
                    Button(onClick = { pack.ensure(this@DesktopLauncherActivity) }) {
                        Text(if (current == AddonPack.State.Failed) "Réessayer" else "Télécharger le module Vulkan")
                    }
                }
            }
        }
    }

    @Composable
    private fun DesktopComponents() {
        var family by rememberSaveable { mutableStateOf("fex") }
        var expanded by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            protons.refreshProtons()
            components.refreshComponents()
            components.refreshComponentCatalog(false)
        }
        SettingsGroup("Installer Proton") {
            if (SessionState.running) Text("Ferme la session Ubuntu pour installer Proton.")
            for (row in protons.protonRows.filter { it.id in setOf("droiddeck", "ge", "cachyos") }) {
                Button(onClick = { protons.installProton(row.id) },
                    enabled = protons.protonBusyId == null && !SessionState.running) {
                    Text(row.name + (row.installed?.let { " · $it" } ?: " · Installer"))
                }
            }
            protons.protonStage?.let { stage ->
                Text(if (protons.protonPercent < 0) stage else "$stage · ${protons.protonPercent} %")
            }
            protons.protonProblem?.let { Text(it) }
        }
        val snapshot = components.compSnapshot
        val selected = snapshot?.protons?.find { it.proton.id == components.compProton }
        SettingsGroup("Proton par défaut") {
            snapshot?.protons.orEmpty().forEach { view ->
                MenuItem(view.proton.name, view == selected) { components.chooseProton(view.proton.id) }
            }
        }
        if (selected != null) {
            Button(enabled = components.compBusy == null && protons.protonBusyId == null, onClick = {
                components.componentAction("Composants de ${selected.proton.name}") {
                    ComponentsManager.downloadForProton(this@DesktopLauncherActivity, selected.proton.dir.name) { _, _ -> }
                    "Vérification des composants terminée pour ${selected.proton.name}"
                }
            }) { Text(components.compBusy ?: "Télécharger les composants compatibles") }
        }
        SettingsGroup("Composants de compatibilité") {
            val families = ComponentsManager.COMPONENTS
            TabStrip(families.map { ComponentsManager.LABEL.getValue(it) }, families.indexOf(family), {
                family = families[it]
                expanded = false
            })
            TextButton(onClick = { components.refreshComponentCatalog() }, enabled = !components.compChecking) {
                Text(if (components.compChecking) "Chargement…" else "Actualiser le catalogue")
            }
            val entries = ComponentsManager.forTool(family, selected?.proton?.dir?.name,
                components.compCatalog.filter { it.comp == family }) { it.file }
            TextButton(onClick = { expanded = !expanded }) { Text("Versions disponibles (${entries.size})") }
            if (expanded) entries.forEach { item ->
                val pct = components.compDownloads[item.file]
                val stored = snapshot?.packages?.any { it.file == item.file } == true
                TextButton(onClick = { components.downloadComponent(item) }, enabled = pct == null && !stored) {
                    Text(item.file + when {
                        stored -> " · Téléchargé"
                        pct == null -> ""
                        pct < 0 -> " · En cours…"
                        else -> " · $pct %"
                    })
                }
            }
            if (selected != null) {
                val packages = ComponentsManager.forTool(family, selected.proton.dir.name,
                    snapshot?.packages.orEmpty().filter { it.comp == family }) { it.file }
                packages.forEach { pkg ->
                    val state = selected.components[family]
                    MenuItem(pkg.version, state?.activeFile == pkg.file, detail = "Appliquer à ${selected.proton.name}") {
                        components.componentAction("Appliquer ${pkg.version}") {
                            ComponentsManager.swap(this@DesktopLauncherActivity, selected.proton.id, pkg.file)
                        }
                    }
                }
            }
        }
    }

    private fun launchUbuntu() {
        storageGranted = hasSharedStorage()
        if (!SessionState.running && !storageGranted) {
            storagePrompt = true
            return
        }
        startUbuntuSession()
    }

    private fun startUbuntuSession() {
        startActivity(Intent(this, SessionActivity::class.java)
            .putExtra(SessionService.EXTRA_MODE, SessionService.MODE_DESKTOP))
    }

    private fun showUpdateStatus(message: String) {
        runOnUiThread {
            if (!isDestroyed && !isFinishing) Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }
}
