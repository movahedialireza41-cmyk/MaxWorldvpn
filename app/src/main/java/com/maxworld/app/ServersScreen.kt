package com.maxworld.app

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.drawBehind
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ServersScreen(onSelected: (VpnConfig) -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val testing = remember { mutableStateListOf<String>() }
    val speedTesting = remember { mutableStateListOf<String>() }
    var speedAllJob by remember { mutableStateOf<Job?>(null) }

    var query by remember { mutableStateOf("") }
    var sort by remember { mutableIntStateOf(0) }
    var filter by remember { mutableStateOf("all") }

    var showAdd by remember { mutableStateOf(false) }
    var showSubs by remember { mutableStateOf(false) }
    var subPrefill by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<VpnConfig?>(null) }
    var editingIsNew by remember { mutableStateOf(false) }
    var qrOf by remember { mutableStateOf<VpnConfig?>(null) }
    var showGroup by remember { mutableStateOf(false) }
    var groupTarget by remember { mutableStateOf<VpnConfig?>(null) }

    fun toast(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()

    fun testOne(c: VpnConfig) {
        if (c.id in testing) return
        testing.add(c.id)
        scope.launch {
            val ms = Tester.delay(c)
            ConfigStore.setPing(c.id, if (ms > 0) ms else -2L)
            testing.remove(c.id)
        }
    }

    suspend fun speedRun(c: VpnConfig) {
        if (c.id in speedTesting) return
        speedTesting.add(c.id)
        try {
            val v = SpeedTester.run(c, Prefs.speedMb(ctx))
            ConfigStore.setSpeed(c.id, v)
        } finally {
            speedTesting.remove(c.id)
        }
    }

    fun speedOne(c: VpnConfig) {
        scope.launch { speedRun(c) }
    }

    var bestJob by remember { mutableStateOf<Job?>(null) }
    var bestDone by remember { mutableIntStateOf(0) }
    var bestTotal by remember { mutableIntStateOf(0) }

    /** Pings every server (6 at a time), then picks the fastest one. */
    fun testAndPickBest(targets: List<VpnConfig>) {
        val list = targets.filter { !it.isGroup() }
        if (list.isEmpty()) return
        bestDone = 0
        bestTotal = list.size
        bestJob = scope.launch {
            try {
                val gate = Semaphore(6)
                list.map { c ->
                    async {
                        gate.withPermit {
                            testing.add(c.id)
                            val ms = Tester.delay(c)
                            ConfigStore.setPing(c.id, if (ms > 0) ms else -2L)
                            testing.remove(c.id)
                            bestDone++
                        }
                    }
                }.awaitAll()
                val best = ConfigStore.configs.filter { it.id in list.map { x -> x.id } && it.ping > 0 }.minByOrNull { it.ping }
                if (best == null) {
                    toast(ctx.getString(R.string.best_none))
                } else {
                    sort = 2
                    if (best.id != ConfigStore.selectedId) {
                        ConfigStore.select(best.id)
                        onSelected(best)
                    }
                    toast(ctx.getString(R.string.best_picked_fmt, best.displayName(), best.ping.toInt()))
                }
            } finally {
                list.forEach { testing.remove(it.id) }
                bestJob = null
            }
        }
    }

    var confirmDead by remember { mutableStateOf(false) }

    fun handleText(text: String) {
        when (val r = Importer.fromText(text)) {
            is Imported.Servers ->
                toast(if (r.count == 0) ctx.getString(R.string.toast_none) else ctx.getString(R.string.toast_added, r.count))
            is Imported.SubUrl -> {
                subPrefill = r.url
                showSubs = true
            }
            Imported.None -> toast(ctx.getString(R.string.toast_none))
        }
    }

    fun paste() {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString().orEmpty()
        handleText(text)
    }

    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { handleText(it) }
    }

    val shown = ConfigStore.configs
        .filter {
            when (filter) {
                "all" -> true
                "fav" -> it.favorite
                else -> it.group == filter
            }
        }
        .filter {
            query.isBlank() || it.displayName().contains(query, true) || it.address.contains(query, true)
        }
        .let { list ->
            when (sort) {
                1 -> list.sortedBy { it.displayName().lowercase() }
                2 -> list.sortedBy { if (it.ping > 0) it.ping else Long.MAX_VALUE }
                3 -> list.sortedByDescending { if (it.speed > 0) it.speed else -1.0 }
                else -> list
            }
        }

    if (confirmDead) {
        val dead = ConfigStore.configs.filter { it.ping == -2L && !it.isGroup() }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDead = false },
            title = { Text(stringResource(R.string.dead_title)) },
            text = { Text(stringResource(R.string.dead_body_fmt, dead.size)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    dead.forEach { ConfigStore.remove(it.id) }
                    confirmDead = false
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmDead = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    Box(Modifier.fillMaxSize()) {
        WorldBackground(connected = false)

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fadeSlideIn()) {
                Text(
                    stringResource(R.string.servers_title),
                    color = p.text,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    stringResource(R.string.sub_count_fmt, ConfigStore.configs.size),
                    color = p.muted,
                    fontSize = 13.sp
                )
            }
            Spacer(Modifier.height(12.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fadeSlideIn(60)
            ) {
                PrimaryButton(
                    text = stringResource(R.string.menu_title),
                    onClick = { showAdd = true },
                    modifier = Modifier.weight(1f)
                )
                if (ConfigStore.configs.isNotEmpty()) {
                    PrimaryButton(
                        text = stringResource(R.string.servers_ping_all),
                        onClick = { shown.forEach { testOne(it) } },
                        filled = false,
                        modifier = Modifier.weight(0.7f)
                    )
                }
            }

            if (ConfigStore.configs.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                SearchBox(query) { query = it }
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Chip(
                        if (bestJob != null) stringResource(R.string.best_running_fmt, bestDone, bestTotal) else stringResource(R.string.best_test),
                        bestJob != null
                    ) {
                        val running = bestJob
                        if (running != null) {
                            running.cancel()
                        } else {
                            testAndPickBest(shown.toList())
                        }
                    }
                    if (ConfigStore.configs.any { it.ping == -2L && !it.isGroup() }) {
                        Chip(stringResource(R.string.dead_remove_fmt, ConfigStore.configs.count { it.ping == -2L && !it.isGroup() }), false) {
                            confirmDead = true
                        }
                    }
                    Chip(stringResource(R.string.sub_title), false) { subPrefill = ""; showSubs = true }
                    Chip(
                        stringResource(if (speedAllJob != null) R.string.servers_stop else R.string.servers_speed_all),
                        speedAllJob != null
                    ) {
                        val running = speedAllJob
                        if (running != null) {
                            running.cancel()
                            speedAllJob = null
                        } else {
                            val targets = shown.toList()
                            speedAllJob = scope.launch {
                                try {
                                    for (c in targets) speedRun(c)
                                } finally {
                                    speedAllJob = null
                                }
                            }
                        }
                    }
                    Chip(stringResource(R.string.filter_all), filter == "all") { filter = "all" }
                    Chip(stringResource(R.string.filter_fav), filter == "fav") { filter = "fav" }
                    Subs.list.forEach { s -> Chip(s.name, filter == s.id) { filter = s.id } }
                    Chip(
                        stringResource(
                            when (sort) {
                                1 -> R.string.sort_name
                                2 -> R.string.sort_ping
                                3 -> R.string.sort_speed
                                else -> R.string.sort_default
                            }
                        ),
                        selected = false
                    ) { sort = (sort + 1) % 4 }
                }
            }
            Spacer(Modifier.height(12.dp))

            if (ConfigStore.configs.isEmpty()) {
                EmptyServers()
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 120.dp)
                ) {
                    items(shown, key = { it.id }) { c ->
                        ServerCard(
                            c = c,
                            selected = c.id == ConfigStore.selectedId,
                            testing = c.id in testing,
                            speedTesting = c.id in speedTesting,
                            onClick = {
                                if (c.id != ConfigStore.selectedId) {
                                    ConfigStore.select(c.id)
                                    onSelected(c)
                                }
                            },
                            onFav = { ConfigStore.toggleFavorite(c.id) },
                            onTest = { testOne(c) },
                            onSpeed = { speedOne(c) },
                            onEdit = {
                                if (c.isGroup()) {
                                    groupTarget = c
                                    showGroup = true
                                } else {
                                    editingIsNew = false
                                    editing = c
                                }
                            },
                            onCopy = { ConfigStore.duplicate(c.id) },
                            onShare = {
                                val send = Intent(Intent.ACTION_SEND)
                                    .setType("text/plain")
                                    .putExtra(Intent.EXTRA_TEXT, LinkBuilder.build(c))
                                ctx.startActivity(Intent.createChooser(send, null))
                            },
                            onQr = { qrOf = c },
                            onCopyLink = {
                                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("MaxWorld", LinkBuilder.build(c)))
                                toast(ctx.getString(R.string.toast_copied))
                            },
                            onDelete = { ConfigStore.remove(c.id) }
                        )
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ dialogs

    if (showAdd) {
        Dialog(onDismissRequest = { showAdd = false }) {
            DialogSurface {
                Text(
                    stringResource(R.string.menu_title),
                    color = p.text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.height(14.dp))
                MenuRow(stringResource(R.string.add_paste)) { showAdd = false; paste() }
                MenuRow(stringResource(R.string.add_scan)) {
                    showAdd = false
                    scanner.launch(
                        ScanOptions()
                            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                            .setOrientationLocked(false)
                            .setBeepEnabled(false)
                            .setPrompt("")
                    )
                }
                MenuRow(stringResource(R.string.add_sub)) { showAdd = false; subPrefill = ""; showSubs = true }
                MenuRow(stringResource(R.string.group_new)) {
                    showAdd = false
                    groupTarget = null
                    showGroup = true
                }
                MenuRow(stringResource(R.string.add_manual)) {
                    showAdd = false
                    editingIsNew = true
                    editing = VpnConfig(name = "", port = 443)
                }
            }
        }
    }

    if (showSubs) {
        SubsDialog(prefillUrl = subPrefill, onDismiss = { showSubs = false })
    }

    if (showGroup) {
        GroupDialog(
            initial = groupTarget,
            onDismiss = { showGroup = false },
            onSave = { g ->
                if (ConfigStore.configs.any { it.id == g.id }) {
                    ConfigStore.update(g)
                    if (g.id == ConfigStore.selectedId) onSelected(g)
                } else {
                    ConfigStore.add(listOf(g))
                }
                showGroup = false
            }
        )
    }

    editing?.let { cfg ->
        EditorDialog(
            initial = cfg,
            isNew = editingIsNew,
            onDismiss = { editing = null },
            onSave = { updated ->
                if (editingIsNew) {
                    ConfigStore.add(listOf(updated))
                } else {
                    ConfigStore.update(updated.copy(ping = -1L))
                    if (updated.id == ConfigStore.selectedId) onSelected(updated)
                }
                editing = null
            }
        )
    }

    qrOf?.let { c ->
        Dialog(onDismissRequest = { qrOf = null }) {
            DialogSurface {
                Text(c.displayName(), color = p.text, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Spacer(Modifier.height(14.dp))
                if (LinkBuilder.isLink(c)) {
                    QrView(LinkBuilder.build(c))
                } else {
                    Text(stringResource(R.string.raw_note), color = p.muted, fontSize = 13.sp)
                }
                Spacer(Modifier.height(14.dp))
                PrimaryButton(stringResource(R.string.close), { qrOf = null })
            }
        }
    }
}

// ---------------------------------------------------------------------- pieces

@Composable
private fun DialogSurface(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(p.cornerDp.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .screenBackdrop()
            .cardBorder(shape)
            .padding(20.dp),
        content = content
    )
}

@Composable
private fun MenuRow(text: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(16.dp)
    Text(
        text = text,
        color = p.text,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(shape)
            .background(p.card)
            .cardBorder(shape)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 16.dp)
    )
}

@Composable
private fun SearchBox(value: String, onChange: (String) -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(p.card)
            .cardBorder(shape)
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        if (value.isEmpty()) {
            Text(stringResource(R.string.search_hint), color = p.muted, fontSize = 14.sp)
        }
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = p.text, fontSize = 14.sp),
            cursorBrush = SolidColor(p.accent),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun StarIcon(filled: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    val color by animateColorAsState(if (filled) p.warn else p.muted, tween(250), label = "star")
    Canvas(
        Modifier
            .size(34.dp)
            .clickable { onClick() }
            .padding(6.dp)
    ) {
        val c = center
        val outer = size.minDimension / 2f
        val inner = outer * 0.45f
        val path = Path()
        for (i in 0 until 10) {
            val r = if (i % 2 == 0) outer else inner
            val a = -PI / 2 + i * PI / 5
            val x = c.x + (r * cos(a)).toFloat()
            val y = c.y + (r * sin(a)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        if (filled) drawPath(path, color) else drawPath(path, color, style = Stroke(width = 1.8.dp.toPx()))
    }
}

@Composable
private fun ServerCard(
    c: VpnConfig,
    selected: Boolean,
    testing: Boolean,
    speedTesting: Boolean,
    onClick: () -> Unit,
    onFav: () -> Unit,
    onTest: () -> Unit,
    onSpeed: () -> Unit,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onQr: () -> Unit,
    onCopyLink: () -> Unit,
    onDelete: () -> Unit
) {
    val p = LocalPalette.current
    val neo = Design.neo
    val shape = RoundedCornerShape((p.cornerDp + (if (neo) 4f else 0f)).dp)
    val border by animateColorAsState(
        if (selected) p.accent else p.cardBorder, tween(350), label = "cardBorder"
    )
    val glow by androidx.compose.animation.core.animateFloatAsState(if (selected) 1f else 0f, tween(450), label = "cardGlow")
    val pingColor = when {
        c.ping == -1L -> p.muted
        c.ping < 0 -> p.danger
        c.ping < 300 -> p.ok
        c.ping < 800 -> p.warn
        else -> p.danger
    }
    val groupName = if (c.group.isEmpty()) "" else Subs.nameOf(c.group)

    Column(
        Modifier
            .fillMaxWidth()
            .then(if (neo) Modifier.fadeSlideIn() else Modifier)
            .clip(shape)
            .background(p.card)
            .then(
                if (neo) {
                    Modifier
                        .background(Brush.horizontalGradient(listOf(p.accent.copy(alpha = 0.20f * glow), Color.Transparent)))
                        .drawBehind {
                            // a coloured edge that shows how fast the server is
                            val w = 4.dp.toPx()
                            val x = if (layoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl) size.width - w else 0f
                            drawRect(pingColor, topLeft = Offset(x, 0f), size = Size(w, size.height))
                        }
                        .border(
                            if (selected) 2.dp else 1.dp,
                            Brush.linearGradient(if (selected) listOf(p.accent, p.accent2) else listOf(p.cardBorder, p.cardBorder)),
                            shape
                        )
                } else {
                    Modifier.border(if (selected) 2.dp else 1.dp, border, shape)
                }
            )
            .clickable { onClick() }
            .padding(start = 8.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StarIcon(c.favorite, onFav)
            Spacer(Modifier.size(6.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    c.displayName(),
                    color = p.text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Spacer(Modifier.height(3.dp))
                val sub = "${c.protocol.uppercase()} · ${c.network} · ${c.security}" +
                    if (groupName.isNotEmpty()) " · $groupName" else ""
                Text(sub, color = p.muted, fontSize = 12.sp, maxLines = 1)
            }
            Column(horizontalAlignment = Alignment.End) {
                if (testing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = p.accent
                    )
                } else {
                    PingBadge(c.ping)
                }
                if (speedTesting) {
                    Spacer(Modifier.height(4.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = p.accent2
                    )
                } else if (c.speed != -1.0) {
                    Spacer(Modifier.height(2.dp))
                    if (c.speed > 0) {
                        Text(
                            stringResource(R.string.mbps_fmt, c.speed),
                            color = p.accent2, fontSize = 13.sp, fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text(stringResource(R.string.speed_failed), color = p.danger, fontSize = 12.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier
                .padding(start = 10.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            ActionText(stringResource(R.string.test), p.accent, onTest)
            ActionText(stringResource(R.string.action_speed), p.accent2, onSpeed)
            ActionText(stringResource(R.string.action_edit), p.text, onEdit)
            if (LinkBuilder.isLink(c)) {
                ActionText(stringResource(R.string.action_qr), p.text, onQr)
                ActionText(stringResource(R.string.action_copy_link), p.text, onCopyLink)
                ActionText(stringResource(R.string.action_share), p.text, onShare)
            }
            ActionText(stringResource(R.string.action_copy), p.text, onCopy)
            ActionText(stringResource(R.string.delete), p.danger, onDelete)
        }
    }
}

@Composable
private fun ActionText(text: String, color: Color, onClick: () -> Unit) {
    Text(
        text,
        color = color,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        modifier = Modifier.clickable { onClick() }
    )
}

@Composable
private fun SubsDialog(prefillUrl: String, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf(prefillUrl) }
    val busy = remember { mutableStateListOf<String>() }

    fun run(sub: Sub) {
        if (sub.id in busy) return
        busy.add(sub.id)
        scope.launch {
            val n = Subs.refresh(sub)
            busy.remove(sub.id)
            Toast.makeText(
                ctx,
                if (n < 0) ctx.getString(R.string.sub_failed) else ctx.getString(R.string.sub_updated_fmt, n),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        DialogSurface {
            Text(
                stringResource(R.string.sub_title),
                color = p.text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.height(12.dp))

            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Subs.list.toList().forEach { s ->
                    val shape = RoundedCornerShape(16.dp)
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .background(p.card)
                            .cardBorder(shape)
                            .padding(14.dp)
                    ) {
                        Text(s.name, color = p.text, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        val last = if (s.updatedAt == 0L) stringResource(R.string.sub_never)
                        else stringResource(
                            R.string.sub_last_fmt,
                            DateUtils.getRelativeTimeSpanString(s.updatedAt).toString()
                        )
                        Text(
                            "${stringResource(R.string.sub_count_fmt, Subs.count(s.id))} · $last",
                            color = p.muted, fontSize = 12.sp
                        )
                        if (s.total > 0L) {
                            val left = (s.total - s.used).coerceAtLeast(0L)
                            Text(
                                stringResource(R.string.sub_data_fmt, Traffic.bytes(left), Traffic.bytes(s.total)),
                                color = p.text, fontSize = 12.sp
                            )
                        }
                        if (s.expire > 0L) {
                            val days = Math.ceil((s.expire - System.currentTimeMillis()) / 86400000.0).toLong()
                            Text(
                                if (days < 0) stringResource(R.string.sub_expired)
                                else stringResource(R.string.sub_days_fmt, days),
                                color = if (days < 0) p.danger else p.muted, fontSize = 12.sp
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (s.id in busy) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = p.accent)
                            } else {
                                ActionText(stringResource(R.string.sub_update), p.accent) { run(s) }
                            }
                            ActionText(stringResource(R.string.sub_copy), p.muted) {
                                try {
                                    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                                        .setPrimaryClip(ClipData.newPlainText("sub", s.url))
                                    Toast.makeText(ctx, R.string.sub_copied, Toast.LENGTH_SHORT).show()
                                } catch (ignored: Exception) {
                                }
                            }
                            ActionText(stringResource(R.string.delete), p.danger) { Subs.remove(s.id) }
                        }
                    }
                }
                if (Subs.list.size > 1) {
                    ActionText(stringResource(R.string.sub_update_all), p.accent) { Subs.list.toList().forEach { run(it) } }
                }
            }

            Spacer(Modifier.height(14.dp))
            Field(stringResource(R.string.sub_name_hint), name, { name = it })
            Spacer(Modifier.height(8.dp))
            Field(stringResource(R.string.sub_url_hint), url, { url = it.trim() }, keyboard = androidx.compose.ui.text.input.KeyboardType.Uri)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton(stringResource(R.string.close), onDismiss, modifier = Modifier.weight(1f), filled = false)
                PrimaryButton(
                    stringResource(R.string.add),
                    onClick = {
                        if (url.startsWith("http://") || url.startsWith("https://")) {
                            val s = Subs.add(name, url)
                            name = ""
                            url = ""
                            run(s)
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun EmptyServers() {
    val p = LocalPalette.current
    Column(
        Modifier
            .fillMaxSize()
            .padding(bottom = 120.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Canvas(Modifier.size(110.dp)) {
            val r = size.minDimension / 2f
            val sw = 3.dp.toPx()
            val brush = Brush.linearGradient(listOf(p.accent, p.accent2))
            drawCircle(brush = brush, radius = r - sw, style = Stroke(sw))
            drawOval(
                brush = brush,
                topLeft = Offset(center.x - r * 0.45f, center.y - r + sw),
                size = androidx.compose.ui.geometry.Size(r * 0.9f, (r - sw) * 2),
                style = Stroke(sw)
            )
            drawLine(brush, Offset(sw, center.y), Offset(size.width - sw, center.y), sw)
        }
        Spacer(Modifier.height(18.dp))
        Text(
            stringResource(R.string.servers_empty_title),
            color = p.text,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.servers_empty_desc),
            color = p.muted,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}
