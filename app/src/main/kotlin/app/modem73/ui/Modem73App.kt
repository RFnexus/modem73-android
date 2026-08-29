package app.modem73.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.modem73.NativeCore
import app.modem73.core.ModemController
import app.modem73.ui.components.Modem73Header
import app.modem73.ui.components.PillTabs
import app.modem73.ui.config.ConfigScreen
import app.modem73.ui.guide.GuideScreen
import app.modem73.ui.model.Page
import app.modem73.ui.rig.RigScreen
import app.modem73.ui.status.StatusScreen
import app.modem73.ui.theme.Modem73Theme
import app.modem73.ui.utils.UtilsScreen

@Composable
fun Modem73App(onStartStop: () -> Unit, darkTheme: Boolean? = null) {
    Modem73Theme(darkTheme = darkTheme ?: isSystemInDarkTheme()) {
        val vm: ModemViewModel = viewModel()
        var page by rememberSaveable { mutableIntStateOf(Page.STATUS.ordinal) }
        var guide by rememberSaveable { mutableStateOf(false) }
        val version = remember { runCatching { "v" + NativeCore.version() }.getOrDefault("v dev") }
        val statusUi by vm.statusUi.collectAsStateWithLifecycle()
        val configUi by vm.configUi.collectAsStateWithLifecycle()
        val utilsUi by vm.utilsUi.collectAsStateWithLifecycle()
        val waterfall by vm.waterfall.collectAsStateWithLifecycle()
        val running by vm.running.collectAsStateWithLifecycle()
        val error by vm.error.collectAsStateWithLifecycle()
        val micDenied by ModemController.micDenied.collectAsStateWithLifecycle()
        val rigTab by vm.rigTab.collectAsStateWithLifecycle()
        val rigUi by vm.rigUi.collectAsStateWithLifecycle()
        val pages = remember(rigTab) { Page.entries.filter { it != Page.RIG || rigTab } }
        if (page >= pages.size) page = 0
        val current = pages[page]
        LaunchedEffect(current) { vm.setRigPoll(current == Page.RIG || current == Page.STATUS) }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .safeDrawingPadding()
        ) {
            Modem73Header(version = version, transmitting = statusUi.transmitting, guideOpen = guide, onGuide = { guide = !guide })
            if (guide) {
                GuideScreen(onClose = { guide = false })
                return@Column
            }
            PillTabs(tabs = pages.map { it.label }, selected = page, onSelect = { page = it })
            Spacer(Modifier.height(4.dp))
            Box(Modifier.weight(1f)) {
                when (current) {
                    Page.STATUS -> StatusScreen(statusUi, waterfall, running, error, micDenied, onStartStop)
                    Page.CONFIG -> ConfigScreen(configUi, vm, onGuide = { guide = true })
                    Page.UTILS -> UtilsScreen(
                        state = utilsUi,
                        enabled = running,
                        onDraftChange = vm::setChatDraft,
                        onSend = vm::sendChat,
                        onSelectSize = vm::setRandomSize,
                        onSendRandom = vm::sendRandom
                    )
                    Page.RIG -> RigScreen(rigUi, running, vm)
                }
            }
        }
    }
}
