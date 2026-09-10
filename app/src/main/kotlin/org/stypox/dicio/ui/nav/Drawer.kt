package org.stypox.dicio.ui.nav

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.stypox.dicio.R
import org.stypox.dicio.util.DiagnosticsLog

@Composable
fun DrawerContent(
    onSettingsClick: () -> Unit,
    onAboutClick: () -> Unit,
    onSpeechToTextPopupClick: () -> Unit,
    closeDrawer: () -> Unit,
) {
    ModalDrawerSheet(
        modifier = Modifier.widthIn(max = 280.dp)
    ) {
        DrawerHeader(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        )

        DrawerItem(
            icon = Icons.Default.Settings,
            label = R.string.settings,
            onClick = {
                onSettingsClick()
                closeDrawer()
            },
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .testTag("settings_drawer_item"),
        )

        DrawerItem(
            icon = Icons.Default.RecordVoiceOver,
            label = R.string.stt_popup,
            onClick = {
                onSpeechToTextPopupClick()
                closeDrawer()
            },
            modifier = Modifier.padding(horizontal = 12.dp),
        )

        DrawerItem(
            icon = Icons.Default.Info,
            label = R.string.about,
            onClick = {
                onAboutClick()
                closeDrawer()
            },
            modifier = Modifier.padding(horizontal = 12.dp),
        )

        DrawerDiagnosticsLog(modifier = Modifier.padding(horizontal = 12.dp))
    }
}

/**
 * Панель диагностики в выезжающем меню: показывает последние записи [DiagnosticsLog],
 * которые автоматически обновляются раз в секунду. Это позволяет видеть цикл работы
 * (wake word -> распознавание -> выбор навыка -> ответ/TTS) прямо на устройстве.
 */
@Composable
private fun DrawerDiagnosticsLog(modifier: Modifier = Modifier) {
    var entries by remember { mutableStateOf(DiagnosticsLog.snapshot()) }
    LaunchedEffect(Unit) {
        while (true) {
            entries = DiagnosticsLog.snapshot()
            delay(1000)
        }
    }

    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = "Диагностика",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
    val last = entries.takeLast(8)
    if (last.isEmpty()) {
        Text(
            text = "(пока пусто — вызовите wake word или подайте команду)",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )
    } else {
        Column(
            modifier = Modifier
                .padding(top = 4.dp, bottom = 8.dp)
                .fillMaxWidth()
        ) {
            for (entry in last.reversed()) {
                Text(
                    text = entry,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Preview
@Composable
private fun DrawerContentPreview() {
    DrawerContent(onSettingsClick = {}, onAboutClick = {}, onSpeechToTextPopupClick = {}, closeDrawer = {})
}

@Preview
@Composable
private fun DrawerHeader(modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.clearAndSetSemantics {}
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            Column(
                modifier = Modifier.weight(1.0f)
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                    maxLines = 1,
                )
                Text(
                    text = stringResource(R.string.drawer_header_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }

            Icon(
                painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .size(40.dp)
                    .scale(2.4f)
            )
        }
    }
}

@Composable
fun DrawerItem(
    icon: ImageVector,
    @StringRes label: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationDrawerItem(
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = stringResource(label),
            )
        },
        label = {
            Text(
                text = stringResource(label),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        selected = false,
        onClick = onClick,
        modifier = modifier,
    )
}
