package com.maxworld.app

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** A rounded single-line text input in the app's style. */
@Composable
fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboard: KeyboardType = KeyboardType.Text
) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(14.dp)
    Column(modifier.fillMaxWidth()) {
        Text(label, color = p.muted, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(p.card)
                .cardBorder(shape)
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = p.text, fontSize = 15.sp),
                cursorBrush = SolidColor(p.accent),
                keyboardOptions = KeyboardOptions(keyboardType = keyboard),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** Selectable pill used for option rows. */
@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(50)
    Text(
        text = text,
        color = if (selected) p.onAccent else p.text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(shape)
            .then(
                if (Design.neo && selected) Modifier.background(Brush.horizontalGradient(listOf(p.accent, p.accent2)))
                else Modifier.background(if (selected) p.accent else p.card)
            )
            .border(1.dp, if (selected) p.accent else p.cardBorder, shape)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

@Composable
private fun ChipRow(title: String, options: List<Pair<String, String>>, current: String, onPick: (String) -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth()) {
        Text(title, color = p.muted, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { (value, label) -> Chip(label, current == value) { onPick(value) } }
        }
    }
}

private val PROTOCOLS = listOf(
    "vless" to "VLESS", "vmess" to "VMess", "trojan" to "Trojan", "shadowsocks" to "Shadowsocks", "wireguard" to "WireGuard", "hysteria2" to "Hysteria2"
)
private val NETWORKS = listOf(
    "tcp" to "TCP", "ws" to "WS", "grpc" to "gRPC", "h2" to "H2",
    "httpupgrade" to "HTTPUpgrade", "xhttp" to "XHTTP", "kcp" to "mKCP"
)
private val SS_METHODS = listOf(
    "aes-128-gcm" to "aes-128-gcm", "aes-256-gcm" to "aes-256-gcm",
    "chacha20-ietf-poly1305" to "chacha20-poly1305", "2022-blake3-aes-128-gcm" to "2022-aes-128",
    "2022-blake3-aes-256-gcm" to "2022-aes-256"
)
private val FINGERPRINTS = listOf("" to "default", "chrome" to "chrome", "firefox" to "firefox", "safari" to "safari", "ios" to "ios", "android" to "android", "random" to "random")

/** Full-screen form for creating or editing one server. */
@Composable
fun EditorDialog(initial: VpnConfig, isNew: Boolean, onDismiss: () -> Unit, onSave: (VpnConfig) -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    var d by remember { mutableStateOf(initial) }
    var portText by remember { mutableStateOf(if (initial.port > 0) initial.port.toString() else "") }
    var aidText by remember { mutableStateOf(initial.alterId.toString()) }
    var mtuText by remember { mutableStateOf(if (initial.wgMtu > 0) initial.wgMtu.toString() else "") }
    val custom = initial.rawJson.isNotEmpty()

    fun pickProtocol(id: String) {
        d = d.copy(
            protocol = id,
            security = if (id == "trojan") "tls" else if (id == "shadowsocks" || id == "wireguard" || id == "hysteria2") "none" else d.security,
            network = if (id == "shadowsocks") "tcp" else if (id == "wireguard" || id == "hysteria2") "udp" else d.network,
            method = when (id) {
                "shadowsocks" -> "aes-256-gcm"
                "vmess" -> "auto"
                else -> ""
            }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .screenBackdrop()
        ) {
            Column(Modifier.fillMaxSize()) {
                Text(
                    stringResource(if (isNew) R.string.new_title else R.string.edit_title),
                    color = p.text,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 10.dp)
                )

                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (isNew) {
                        ChipRow(stringResource(R.string.f_protocol), PROTOCOLS, d.protocol) { pickProtocol(it) }
                    }
                    Field(stringResource(R.string.f_name), d.name, { d = d.copy(name = it) })

                    if (custom) {
                        Text(stringResource(R.string.raw_note), color = p.muted, fontSize = 13.sp)
                    } else {
                        Field(stringResource(R.string.f_address), d.address, { d = d.copy(address = it.trim()) }, keyboard = KeyboardType.Uri)
                        Field(stringResource(R.string.f_port), portText, { portText = it.filter(Char::isDigit).take(5) }, keyboard = KeyboardType.Number)

                        when (d.protocol) {
                            "vless" -> {
                                Field(stringResource(R.string.f_uuid), d.userId, { d = d.copy(userId = it.trim()) })
                                Field(stringResource(R.string.f_flow), d.flow, { d = d.copy(flow = it.trim()) })
                                Field(stringResource(R.string.f_encryption), d.encryption, { d = d.copy(encryption = it.trim()) })
                            }
                            "vmess" -> {
                                Field(stringResource(R.string.f_uuid), d.userId, { d = d.copy(userId = it.trim()) })
                                Field(
                                    stringResource(R.string.f_alterid), aidText,
                                    { aidText = it.filter(Char::isDigit).take(4); d = d.copy(alterId = aidText.toIntOrNull() ?: 0) },
                                    keyboard = KeyboardType.Number
                                )
                                ChipRow(
                                    stringResource(R.string.f_method),
                                    listOf("auto", "aes-128-gcm", "chacha20-poly1305", "none").map { it to it },
                                    d.method.ifEmpty { "auto" }
                                ) { d = d.copy(method = it) }
                            }
                            "trojan" -> Field(stringResource(R.string.f_password), d.userId, { d = d.copy(userId = it) })
                            "hysteria2" -> {
                                Field(stringResource(R.string.f_password), d.userId, { d = d.copy(userId = it) })
                                Field(stringResource(R.string.f_sni), d.sni, { d = d.copy(sni = it.trim()) })
                                Field(stringResource(R.string.f_hy_obfs), d.hyObfsPass, { d = d.copy(hyObfsPass = it) })
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        stringResource(R.string.f_insecure),
                                        color = p.text, fontSize = 14.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Switch(
                                        checked = d.allowInsecure,
                                        onCheckedChange = { d = d.copy(allowInsecure = it) },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = p.onAccent, checkedTrackColor = p.accent,
                                            uncheckedThumbColor = p.muted, uncheckedTrackColor = p.track
                                        )
                                    )
                                }
                            }
                            "wireguard" -> {
                                Field(stringResource(R.string.f_wg_private), d.userId, { d = d.copy(userId = it.trim()) })
                                Field(stringResource(R.string.f_wg_peer), d.publicKey, { d = d.copy(publicKey = it.trim()) })
                                Field(stringResource(R.string.f_wg_address), d.wgAddress, { d = d.copy(wgAddress = it.trim()) })
                                Field(stringResource(R.string.f_wg_psk), d.wgPsk, { d = d.copy(wgPsk = it.trim()) })
                                Field(stringResource(R.string.f_wg_reserved), d.wgReserved, { d = d.copy(wgReserved = it.trim()) })
                                Field(
                                    stringResource(R.string.f_wg_mtu), mtuText,
                                    { mtuText = it.filter(Char::isDigit).take(4); d = d.copy(wgMtu = mtuText.toIntOrNull() ?: 0) },
                                    keyboard = KeyboardType.Number
                                )
                            }
                            "shadowsocks" -> {
                                Field(stringResource(R.string.f_password), d.userId, { d = d.copy(userId = it) })
                                ChipRow(stringResource(R.string.f_method), SS_METHODS, d.method) { d = d.copy(method = it) }
                            }
                        }

                        if (d.protocol != "shadowsocks" && d.protocol != "wireguard" && d.protocol != "hysteria2") {
                            ChipRow(stringResource(R.string.f_network), NETWORKS, d.network) { d = d.copy(network = it) }
                            when (d.network) {
                                "ws", "h2", "httpupgrade", "xhttp" -> {
                                    Field(stringResource(R.string.f_host), d.host, { d = d.copy(host = it.trim()) })
                                    Field(stringResource(R.string.f_path), d.path, { d = d.copy(path = it.trim()) })
                                    if (d.network == "xhttp") {
                                        ChipRow(
                                            stringResource(R.string.f_xhttpmode),
                                            listOf("auto", "packet-up", "stream-up", "stream-one").map { it to it },
                                            d.xhttpMode.ifEmpty { "auto" }
                                        ) { d = d.copy(xhttpMode = it) }
                                    }
                                }
                                "grpc" -> {
                                    Field(stringResource(R.string.f_servicename), d.serviceName, { d = d.copy(serviceName = it.trim()) })
                                    Field(stringResource(R.string.f_authority), d.authority, { d = d.copy(authority = it.trim()) })
                                    ChipRow(
                                        "gRPC mode",
                                        listOf("gun" to "gun", "multi" to "multi"),
                                        d.grpcMode.ifEmpty { "gun" }
                                    ) { d = d.copy(grpcMode = it) }
                                }
                                "tcp" -> {
                                    ChipRow(
                                        stringResource(R.string.f_header),
                                        listOf("" to "none", "http" to "http"),
                                        d.headerType
                                    ) { d = d.copy(headerType = it) }
                                    if (d.headerType == "http") {
                                        Field(stringResource(R.string.f_host), d.host, { d = d.copy(host = it.trim()) })
                                        Field(stringResource(R.string.f_path), d.path, { d = d.copy(path = it.trim()) })
                                    }
                                }
                                "kcp" -> {
                                    Field(stringResource(R.string.f_header), d.headerType, { d = d.copy(headerType = it.trim()) })
                                    Field(stringResource(R.string.f_path), d.path, { d = d.copy(path = it.trim()) })
                                }
                            }

                            ChipRow(
                                stringResource(R.string.f_security),
                                listOf("none" to stringResource(R.string.sec_none), "tls" to "TLS", "reality" to "Reality"),
                                d.security
                            ) { d = d.copy(security = it) }

                            if (d.security == "tls" || d.security == "reality") {
                                Field(stringResource(R.string.f_sni), d.sni, { d = d.copy(sni = it.trim()) })
                                ChipRow(stringResource(R.string.f_fp), FINGERPRINTS, d.fingerprint) { d = d.copy(fingerprint = it) }
                            }
                            if (d.security == "tls") {
                                Field(stringResource(R.string.f_alpn), d.alpn, { d = d.copy(alpn = it.trim()) })
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        stringResource(R.string.f_insecure),
                                        color = p.text, fontSize = 14.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Switch(
                                        checked = d.allowInsecure,
                                        onCheckedChange = { d = d.copy(allowInsecure = it) },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = p.onAccent, checkedTrackColor = p.accent,
                                            uncheckedThumbColor = p.muted, uncheckedTrackColor = p.track
                                        )
                                    )
                                }
                            }
                            if (d.security == "reality") {
                                Field(stringResource(R.string.f_pbk), d.publicKey, { d = d.copy(publicKey = it.trim()) })
                                Field(stringResource(R.string.f_sid), d.shortId, { d = d.copy(shortId = it.trim()) })
                                Field(stringResource(R.string.f_spx), d.spiderX, { d = d.copy(spiderX = it.trim()) })
                            }
                        } else {
                            // Shadowsocks has no transport/security options here.
                            Spacer(Modifier.height(0.dp))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PrimaryButton(
                        stringResource(R.string.cancel), onDismiss,
                        modifier = Modifier.weight(1f), filled = false
                    )
                    PrimaryButton(
                        stringResource(R.string.save),
                        onClick = {
                            val port = portText.toIntOrNull() ?: 0
                            val wgOk = d.protocol != "wireguard" || (d.publicKey.isNotBlank() && d.wgAddress.isNotBlank())
                            val ok = custom || (d.address.isNotBlank() && port in 1..65535 && d.userId.isNotBlank() && wgOk)
                            if (!ok) {
                                Toast.makeText(ctx, R.string.err_invalid, Toast.LENGTH_SHORT).show()
                            } else {
                                onSave(if (custom) d else d.copy(port = port))
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
