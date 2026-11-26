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
    PLASMA("plasma", "KDE Plasma", "Interface complète · Wayland", emptyList(), emptyList()),
    XFCE("xfce", "XFCE", "Bureau léger · X11", listOf("xfce4-session", "xfwm4", "xfce4-panel", "xfdesktop", "thunar", "xfce4-terminal"),
        listOf("xfce4", "xfce4-terminal")),
    LXQT("lxqt", "LXQt", "Bureau léger · Qt / X11", listOf("startlxqt", "lxqt-session", "lxqt-panel", "pcmanfm-qt", "openbox", "qterminal"),
        listOf("lxqt-core", "openbox", "qterminal"));

    fun installed(context: Context): Boolean {
        if (this == PLASMA) return com.droiddeck.launcher.runtime.DesktopCatalog.desktopInstalled(context)
        val root = LinuxRuntime.rootDir(context)
        return (executables + listOf("Xwayland", "xdpyinfo", "xauth", "mcookie")).all {
            File(root, "usr/bin/$it").isFile
        }
    }

    companion object {
        fun selected(context: Context): DroiDesktop {
            val id = context.getSharedPreferences("droi-desktop", Context.MODE_PRIVATE).getString("environment", "plasma")
            return values().firstOrNull { it.id == id } ?: PLASMA
        }
        fun select(context: Context, desktop: DroiDesktop) {
            context.getSharedPreferences("droi-desktop", Context.MODE_PRIVATE).edit()
                .putString("environment", desktop.id).apply()
        }
    }
}

class DesktopLauncherActivity : ComponentActivity() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val components by lazy { ComponentsMenu(this, mainHandler) }
    private val protons by lazy { ProtonMenu(this, mainHandler, onInstalled = { components.refreshComponents() }) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DroidDeckTheme {
                var tab by rememberSaveable { mutableIntStateOf(0) }
                var desktopChoice by rememberSaveable { mutableStateOf(false) }
                var selectedId by rememberSaveable { mutableStateOf(DroiDesktop.selected(this@DesktopLauncherActivity).id) }
                var automatic by remember { mutableStateOf(DroiUpdates.isEnabled(this)) }
                androidx.activity.compose.BackHandler(enabled = tab != 0) { tab = 0 }
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Droi Desktop", style = MaterialTheme.typography.headlineMedium)
                        TabStrip(listOf("Bureaux", "Composants", "Paramètres"), tab, { tab = it })
                        if (tab == 0) {
                            SettingsGroup("Bureaux Linux") {
                                val selected = DroiDesktop.selected(this@DesktopLauncherActivity)
                                MenuItem("Ubuntu", false, detail = selected.label) {
                                    if (SessionState.running) launchUbuntu() else desktopChoice = true
                                }
                                Text("Choisis ton environnement de bureau avant le lancement.")
                                if (desktopChoice) {
                                    AlertDialog(
                                        onDismissRequest = { desktopChoice = false },
                                        title = { Text("Interface Ubuntu") },
                                        text = {
                                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                DroiDesktop.values().forEach { choice ->
                                                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                                        RadioButton(selected = selectedId == choice.id, onClick = { selectedId = choice.id })
                                                        Column(Modifier.weight(1f)) {
                                                            Text(choice.label, style = MaterialTheme.typography.titleMedium)
                                                            Text(choice.description, style = MaterialTheme.typography.bodySmall)
                                                            Text(if (choice.installed(this@DesktopLauncherActivity)) "Installé" else "Installation au premier lancement",
                                                                style = MaterialTheme.typography.bodySmall)
                                                        }
                                                    }
                                                }
                                                Text("Tes fichiers, applications et préfixes Wine sont conservés.", style = MaterialTheme.typography.bodySmall)
                                            }
                                        },
                                        confirmButton = {
                                            Button(onClick = {
                                                val choice = DroiDesktop.values().firstOrNull { it.id == selectedId } ?: DroiDesktop.PLASMA
                                                if (!SessionState.running && SessionState.installing == null) {
                                                    DroiDesktop.select(this@DesktopLauncherActivity, choice)
                                                    desktopChoice = false
                                                    launchUbuntu()
                                                } else {
                                                    Toast.makeText(this@DesktopLauncherActivity, "Une session ou installation est déjà en cours.", Toast.LENGTH_SHORT).show()
                                                }
                                            }) { Text("Lancer Ubuntu") }
                                        },
                                        dismissButton = { TextButton(onClick = { desktopChoice = false }) { Text("Annuler") } },
                                    )
                                }
                            }
                        } else if (tab == 1) {
                            DesktopComponents()
                        } else {
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
        DroiUpdates.check(this)
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
        startActivity(Intent(this, SessionActivity::class.java)
            .putExtra(SessionService.EXTRA_MODE, SessionService.MODE_DESKTOP))
    }

    private fun showUpdateStatus(message: String) {
        runOnUiThread {
            if (!isDestroyed && !isFinishing) Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }
}
