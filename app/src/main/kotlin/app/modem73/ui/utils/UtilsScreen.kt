package app.modem73.ui.utils

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.modem73.ui.components.SectionTitle
import app.modem73.ui.model.ChatDirection
import app.modem73.ui.model.ChatLine
import app.modem73.ui.model.UtilsUiState
import app.modem73.ui.theme.Modem73Colors

@Composable
fun UtilsScreen(
    state: UtilsUiState,
    enabled: Boolean,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onSelectSize: (Int) -> Unit,
    onSendRandom: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        SectionTitle("TEST CHAT")
        ChatLog(
            lines = state.chatLines,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )
        SectionTitle("LOG")
        LogPanel(
            lines = state.logLines,
            modifier = Modifier
                .weight(0.7f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(8.dp))
        ChatComposer(draft = state.draft, enabled = enabled, onDraftChange = onDraftChange, onSend = onSend, modifier = Modifier.padding(horizontal = 16.dp))
        SectionTitle("SEND RANDOM DATA")
        RandomDataPanel(
            sizes = state.randomSizes,
            selected = state.selectedRandomSize,
            enabled = enabled,
            onSelect = onSelectSize,
            onSend = onSendRandom,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun LogPanel(lines: List<String>, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.scrollToItem(lines.size - 1)
    }
    LazyColumn(
        state = listState,
        modifier = modifier.padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items(lines) { line ->
            Text(
                text = line,
                color = when {
                    line.startsWith("(!)") && line.contains("PTT") -> Modem73Colors.tx
                    line.startsWith("(!)") -> Modem73Colors.warn
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 14.sp
            )
        }
    }
}

@Composable
private fun ChatLog(lines: List<ChatLine>, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(lines) { line -> ChatRow(line) }
    }
}

@Composable
private fun ChatRow(line: ChatLine) {
    val tint = when (line.direction) {
        ChatDirection.SENT -> Modem73Colors.tx
        ChatDirection.RECEIVED -> Modem73Colors.rx
        ChatDirection.SYSTEM -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(verticalAlignment = Alignment.Top) {
        Text(
            text = line.time,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = when (line.direction) {
                        ChatDirection.SENT -> "->"
                        ChatDirection.RECEIVED -> "<-"
                        ChatDirection.SYSTEM -> ""
                    },
                    color = tint,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (line.direction == ChatDirection.SYSTEM) "" else "M73:${line.callsign}",
                    color = tint,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                if (line.snrDb != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = String.format("%.1f dB", line.snrDb),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Text(
                text = line.text,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun ChatComposer(draft: String, enabled: Boolean, onDraftChange: (String) -> Unit, onSend: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextField(
            value = draft,
            onValueChange = onDraftChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            placeholder = { Text("Message") },
            prefix = { Text("M73:", color = Modem73Colors.tx, fontWeight = FontWeight.Bold) },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.background,
                unfocusedContainerColor = MaterialTheme.colorScheme.background,
                focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                unfocusedIndicatorColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            shape = RectangleShape
        )
        Spacer(Modifier.width(8.dp))
        Button(
            onClick = onSend,
            enabled = enabled && draft.isNotBlank(),
            shape = RectangleShape,
            modifier = Modifier.height(56.dp),
            elevation = null,
            colors = ButtonDefaults.buttonColors(containerColor = Modem73Colors.tx, contentColor = Modem73Colors.onAccent)
        ) {
            Text("SEND", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun RandomDataPanel(sizes: List<Int>, selected: Int, enabled: Boolean, onSelect: (Int) -> Unit, onSend: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            sizes.forEach { size ->
                val active = size == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background)
                        .clickable { onSelect(size) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (size >= 1024) "${size / 1024}K" else "$size",
                        color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = onSend,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RectangleShape,
            elevation = null,
            colors = ButtonDefaults.buttonColors(containerColor = Modem73Colors.tx, contentColor = Modem73Colors.onAccent)
        ) {
            Text("SEND $selected RANDOM BYTES", fontWeight = FontWeight.Bold)
        }
    }
}
