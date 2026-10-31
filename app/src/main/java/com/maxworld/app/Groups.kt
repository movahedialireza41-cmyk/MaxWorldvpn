package com.maxworld.app

import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Build a chain (A -> B -> C, each server connects through the previous one) or a load-balanced
 * group (traffic spread across several servers) out of servers you already have.
 */
@Composable
fun GroupDialog(initial: VpnConfig?, onDismiss: () -> Unit, onSave: (VpnConfig) -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current

    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var type by remember { mutableStateOf(initial?.protocol?.takeIf { it == "chain" || it == "balance" } ?: "chain") }
    var strategy by remember { mutableStateOf(initial?.method?.ifEmpty { "random" } ?: "random") }
    val picked = remember {
        mutableStateListOf<String>().apply {
            initial?.members?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.let { addAll(it) }
        }
    }
    val eligible = ConfigStore.configs.filter { !it.isGroup() && it.rawJson.isEmpty() }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .screenBackdrop()
        ) {
            Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                Text(
                    stringResource(if (initial == null) R.string.group_new else R.string.group_edit),
                    color = p.text,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(top = 22.dp, bottom = 10.dp)
                )
                Field(stringResource(R.string.f_name), name, { name = it })
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(stringResource(R.string.group_chain), type == "chain") { type = "chain" }
                    Chip(stringResource(R.string.group_balance), type == "balance") { type = "balance" }
                }
                Spacer(Modifier.height(8.dp))
                if (type == "chain") {
                    Text(stringResource(R.string.group_chain_desc), color = p.muted, fontSize = 12.sp)
                } else {
                    Text(stringResource(R.string.group_strategy), color = p.muted, fontSize = 12.sp)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip(stringResource(R.string.st_random), strategy == "random") { strategy = "random" }
                        Chip(stringResource(R.string.st_round), strategy == "roundRobin") { strategy = "roundRobin" }
                        Chip(stringResource(R.string.st_ping), strategy == "leastPing") { strategy = "leastPing" }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.group_pick), color = p.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))

                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(eligible, key = { it.id }) { c ->
                        val index = picked.indexOf(c.id)
                        val on = index >= 0
                        val shape = RoundedCornerShape(14.dp)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(shape)
                                .background(p.card)
                                .border(1.dp, if (on) p.accent else p.cardBorder, shape)
                                .clickable { if (on) picked.remove(c.id) else picked.add(c.id) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (on) "${index + 1}" else "·",
                                color = if (on) p.accent else p.muted,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.size(28.dp)
                            )
                            Column(Modifier.weight(1f)) {
                                Text(c.displayName(), color = p.text, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                Text(
                                    "${c.protocol.uppercase()} · ${c.network} · ${c.security}",
                                    color = p.muted, fontSize = 11.sp, maxLines = 1
                                )
                            }
                        }
                    }
                }

                Row(
                    Modifier.fillMaxWidth().padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PrimaryButton(stringResource(R.string.cancel), onDismiss, modifier = Modifier.weight(1f), filled = false)
                    PrimaryButton(
                        stringResource(R.string.save),
                        onClick = {
                            if (picked.size < 2) {
                                Toast.makeText(ctx, R.string.err_group, Toast.LENGTH_SHORT).show()
                            } else {
                                val label = name.ifBlank {
                                    ctx.getString(if (type == "chain") R.string.group_chain else R.string.group_balance)
                                }
                                val base = initial ?: VpnConfig()
                                onSave(
                                    base.copy(
                                        name = label,
                                        protocol = type,
                                        members = picked.joinToString(","),
                                        method = if (type == "balance") strategy else "",
                                        network = "group",
                                        security = picked.size.toString(),
                                        address = "",
                                        port = 0,
                                        ping = -1L,
                                        speed = -1.0
                                    )
                                )
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
