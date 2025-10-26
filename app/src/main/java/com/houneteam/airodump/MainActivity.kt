package com.houneteam.airodump

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.houneteam.airodump.ui.theme.AirodumpTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AirodumpTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ScannerScreen(
                        onStartScan = { iface ->
                            val i = Intent(this, ScanService::class.java).apply {
                                action = ScanService.ACTION_START
                                putExtra(ScanService.EXTRA_IFACE, iface)
                            }
                            startService(i)
                        },
                        onStopScan = {
                            val i = Intent(this, ScanService::class.java).apply {
                                action = ScanService.ACTION_STOP
                            }
                            startService(i)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ScannerScreen(
    viewModel: ScanViewModel = viewModel(),
    onStartScan: (String) -> Unit,
    onStopScan: () -> Unit
) {
    val apList      by viewModel.apList.collectAsState()
    val ifaceList   by viewModel.ifaceList.collectAsState()
    val statusState by viewModel.status.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refreshAll()
    }

    var ifaceMenuExpanded by remember { mutableStateOf(false) }
    var selectedIface by remember(ifaceList) {
        mutableStateOf(ifaceList.firstOrNull().orEmpty())
    }

    var scanning by remember { mutableStateOf(false) }

    val monitorOn = remember(statusState.monitorIface) {
        !statusState.monitorIface.isNullOrBlank()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        Text(
            text = "Airodump Dashboard",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(12.dp)) {
                Text("Environment status", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Chroot: " + if (statusState.chrootFound) "OK" else "NOT FOUND",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "airodump-ng: " + if (statusState.airodumpFound) "OK" else "NOT FOUND",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "Monitor iface: " + (statusState.monitorIface ?: "none"),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {

            Box {
                Button(
                    onClick = { ifaceMenuExpanded = true },
                    enabled = !scanning
                ) {
                    Text(if (selectedIface.isBlank()) "no iface" else selectedIface)
                }

                DropdownMenu(
                    expanded = ifaceMenuExpanded,
                    onDismissRequest = { ifaceMenuExpanded = false }
                ) {
                    ifaceList.forEach { iface ->
                        DropdownMenuItem(
                            text = { Text(iface) },
                            onClick = {
                                selectedIface = iface
                                ifaceMenuExpanded = false
                            }
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.padding(start = 12.dp)
            ) {
                Row(
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Text(
                        text = "Monitor mode",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Switch(
                        checked = monitorOn,
                        enabled = !scanning && selectedIface.isNotBlank(),
                        onCheckedChange = { enable ->
                            viewModel.setMonitorEnabled(enable, selectedIface)
                        }
                    )
                }
                Text(
                    text = if (monitorOn)
                        "ON (${statusState.monitorIface})"
                    else
                        "OFF",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {

            if (!scanning) {
                Button(
                    onClick = {
                        if (selectedIface.isNotBlank()) {
                            scanning = true
                            onStartScan(selectedIface)
                        }
                    }
                ) {
                    Text("Start scan")
                }
            } else {
                Button(
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    onClick = {
                        scanning = false
                        onStopScan()
                    }
                ) {
                    Text("Stop")
                }
            }

            Text(
                text = if (scanning && selectedIface.isNotBlank())
                    "Status: scanning on $selectedIface"
                else
                    "Status: idle",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Visible APs (${apList.size})",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            items(apList) { ap ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            text = ap.essid.ifBlank { "<hidden>" },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "BSSID: ${ap.bssid}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "CH ${ap.channel} | PWR ${ap.power} | ENC ${ap.privacy}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}
