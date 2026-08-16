package com.meshlit.ui.screens.mcp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meshlit.design.MeshlitDesignPalette
import com.meshlit.design.MeshlitGlassCard
import com.meshlit.design.StitchPalette
import com.meshlit.design.stitchPulseGlow

/**
 * Stitch-parity MCP tool server view with 3 tabs:
 *   - Tools (list of registered tools, tap to select)
 *   - RPC Tester (JSON-RPC 2.0 editor for the selected tool)
 *   - Logs (rolling JSON-RPC traffic feed)
 *
 * Mirror of
 * `stitch/meshlit---federated-edge-ai-cluster/src/components/MCPServerView.tsx`.
 * Bound to `v2/mcp` in the NavHost.
 */
@Composable
fun MeshlitV2MCPServerScreen(palette: StitchPalette = StitchPalette.DARK) {
    val isDark = palette == StitchPalette.DARK
    var selectedTab by remember { mutableStateOf(McpTab.Tools) }
    var selectedTool by remember { mutableStateOf(McpTools.all.first()) }
    var rpcParams by remember { mutableStateOf("""{
  "node_id": "all",
  "include_thermal": true
}""") }
    var logs by remember { mutableStateOf(McpTools.sampleLogs()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        Column {
            Text(
                text = "MCP Tool Server",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            )
            Text(
                text = "JSON-RPC 2.0 over the embedded :8080 endpoint",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))

        // Tab bar.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            McpTab.values().forEach { tab ->
                val sel = tab == selectedTab
                Box(
                    modifier = Modifier
                        .stitchPulseGlow(
                            enabled = sel,
                            cyan = MeshlitDesignPalette.iridescentStart,
                            purple = MeshlitDesignPalette.iridescentMid,
                        )
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (sel) MeshlitDesignPalette.iridescentStart.copy(alpha = 0.18f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        )
                        .border(
                            1.dp,
                            if (sel) MeshlitDesignPalette.iridescentStart else Color.Transparent,
                            RoundedCornerShape(50),
                        )
                        .clickable { selectedTab = tab }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = tab.label,
                        color = if (sel) MeshlitDesignPalette.iridescentStart else MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium,
                        fontSize = 12.sp,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        when (selectedTab) {
            McpTab.Tools -> ToolsList(
                palette = palette,
                selected = selectedTool,
                onSelect = { tool ->
                    selectedTool = tool
                    rpcParams = tool.sampleJson()
                },
            )
            McpTab.RpcTester -> RpcTester(
                palette = palette,
                tool = selectedTool,
                params = rpcParams,
                onParamsChange = { rpcParams = it },
                onSend = {
                    logs = listOf(
                        McpLog(
                            timestamp = System.currentTimeMillis(),
                            direction = McpLogDirection.Out,
                            tool = selectedTool.name,
                            payload = """{"jsonrpc":"2.0","method":"${selectedTool.name}","params":$rpcParams}""",
                        ),
                        McpLog(
                            timestamp = System.currentTimeMillis() + 1,
                            direction = McpLogDirection.In,
                            tool = selectedTool.name,
                            payload = """{"jsonrpc":"2.0","result":{"ok":true,"tool":"${selectedTool.name}","data":{}}}""",
                        ),
                    ) + logs
                },
            )
            McpTab.Logs -> LogsList(palette = palette, logs = logs)
        }
    }
}

enum class McpTab(val label: String) {
    Tools("Tools"),
    RpcTester("RPC Tester"),
    Logs("Logs"),
}

enum class McpLogDirection { In, Out }

data class McpLog(
    val timestamp: Long,
    val direction: McpLogDirection,
    val tool: String,
    val payload: String,
)

data class McpToolDef(
    val name: String,
    val description: String,
    val category: String,
) {
    fun sampleJson(): String = when (name) {
        "device_status" -> """{
  "node_id": "all",
  "include_thermal": true
}"""
        "cluster_dispatch" -> """{
  "prompt": "Explain the cluster topology",
  "model_id": "qwen2.5-1.5b-instruct",
  "max_tokens": 256
}"""
        "pcap_capture" -> """{
  "duration_s": 30,
  "iface": "tun0"
}"""
        "file_read" -> """{
  "path": "/sdcard/Download/example.txt",
  "max_bytes": 4096
}"""
        "saf_import" -> """{
  "tree_uri": "content://..."
}"""
        "network_ping" -> """{
  "host": "192.168.1.42",
  "count": 4
}"""
        "battery_check" -> """{
  "node_id": "all"
}"""
        else -> "{}"
    }
}

object McpTools {
    val all: List<McpToolDef> = listOf(
        McpToolDef("device_status", "Live CPU/RAM/battery/temp per node", "cluster"),
        McpToolDef("cluster_dispatch", "Submit prompt across cluster shards", "cluster"),
        McpToolDef("pcap_capture", "Capture packets to .pcap for Wireshark", "network"),
        McpToolDef("network_ping", "Latency probe a peer", "network"),
        McpToolDef("file_read", "Read a file inside the SAF vault", "files"),
        McpToolDef("saf_import", "Import a model via SAF tree URI", "files"),
        McpToolDef("battery_check", "Battery percent + charging state", "device"),
    )

    fun sampleLogs(): List<McpLog> = listOf(
        McpLog(
            timestamp = System.currentTimeMillis() - 60_000,
            direction = McpLogDirection.In,
            tool = "device_status",
            payload = """{"jsonrpc":"2.0","result":{"ok":true,"data":{"battery":82,"temp_c":34.2}}}""",
        ),
        McpLog(
            timestamp = System.currentTimeMillis() - 32_000,
            direction = McpLogDirection.Out,
            tool = "cluster_dispatch",
            payload = """{"jsonrpc":"2.0","method":"cluster_dispatch","params":{"prompt":"hi"}}""",
        ),
    )
}

@Composable
private fun ToolsList(
    palette: StitchPalette,
    selected: McpToolDef,
    onSelect: (McpToolDef) -> Unit,
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(McpTools.all, key = { it.name }) { tool ->
            val isSelected = tool.name == selected.name
            MeshlitGlassCard(palette = palette, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(tool) }
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                if (isSelected) MeshlitDesignPalette.iridescentStart else MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(50),
                            ),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = tool.name,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = tool.description,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                        )
                    }
                    Text(
                        text = tool.category,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun RpcTester(
    palette: StitchPalette,
    tool: McpToolDef,
    params: String,
    onParamsChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        MeshlitGlassCard(palette = palette, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Terminal,
                        contentDescription = null,
                        tint = MeshlitDesignPalette.iridescentStart,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = tool.name,
                        color = MeshlitDesignPalette.iridescentStart,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (palette == StitchPalette.DARK)
                                MeshlitDesignPalette.Dark.canvasDeep.copy(alpha = 0.6f)
                            else
                                Color.White.copy(alpha = 0.8f)
                        )
                        .border(
                            1.dp,
                            MeshlitDesignPalette.iridescentStart.copy(alpha = 0.4f),
                            RoundedCornerShape(10.dp),
                        )
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = params,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Box(
                        modifier = Modifier
                            .stitchPulseGlow(
                                enabled = true,
                                cyan = MeshlitDesignPalette.iridescentStart,
                                purple = MeshlitDesignPalette.iridescentMid,
                            )
                            .clip(RoundedCornerShape(50))
                            .background(MeshlitDesignPalette.iridescentStart.copy(alpha = 0.25f))
                            .border(1.dp, MeshlitDesignPalette.iridescentStart, RoundedCornerShape(50))
                            .clickable(onClick = onSend)
                            .padding(horizontal = 18.dp, vertical = 8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Send,
                                contentDescription = null,
                                tint = MeshlitDesignPalette.iridescentStart,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Send",
                                color = MeshlitDesignPalette.iridescentStart,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogsList(palette: StitchPalette, logs: List<McpLog>) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(logs) { log ->
            MeshlitGlassCard(palette = palette, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = if (log.direction == McpLogDirection.In)
                                Icons.Outlined.CheckCircle
                            else
                                Icons.Outlined.Wifi,
                            contentDescription = null,
                            tint = if (log.direction == McpLogDirection.In)
                                MeshlitDesignPalette.iridescentEnd
                            else
                                MeshlitDesignPalette.Dark.textAmber,
                            modifier = Modifier.size(12.dp),
                        )
                        Text(
                            text = log.tool,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = log.payload,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                    )
                }
            }
        }
    }
}