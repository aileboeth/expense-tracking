package com.aileboeth.expensetracking.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aileboeth.expensetracking.bubble.Bubble
import com.aileboeth.expensetracking.data.Attachment
import com.aileboeth.expensetracking.data.Categorizer
import com.aileboeth.expensetracking.data.CategoryStyle
import com.aileboeth.expensetracking.data.Kinds
import com.aileboeth.expensetracking.data.Money
import com.aileboeth.expensetracking.data.Repo
import com.aileboeth.expensetracking.data.Status
import com.aileboeth.expensetracking.data.Tx
import com.aileboeth.expensetracking.parse.TxType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val DAY = 24L * 3600 * 1000

/** Add (initial = null) or edit one entry; also used to fill in a pending amount. Full screen. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TxEditor(initial: Tx?, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var type by remember { mutableStateOf(initial?.type ?: TxType.EXPENSE) }
    var amount by remember { mutableStateOf(initial?.amountCents?.let { Money.format(it) } ?: "") }
    // An automatic entry parked in 其他 counts as "not chosen", so a note can still decide it.
    var category by remember { mutableStateOf(initial?.category?.takeUnless { it == "其他" && initial.source == "auto" } ?: "") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var time by remember { mutableLongStateOf(initial?.time ?: System.currentTimeMillis()) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val categories = repoQuery(type, initial = emptyList<String>()) { Repo.categories(type) }
    val recentNotes = repoQuery(type, initial = emptyList<String>()) { Repo.recentNotes(type, System.currentTimeMillis() - 90 * DAY, 10) }

    // Which category 保存 uses when none is picked: from the note (rules, learned, built-in words).
    var noteGuess by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(note, type) {
        delay(150)
        noteGuess = withContext(Dispatchers.IO) {
            note.trim().takeIf { it.isNotEmpty() }?.let { Categorizer.fromNote(type, it, initial?.counterpart.orEmpty()) }
        }
    }

    // Attachments: new ones are files already copied in (id 0); they are linked or deleted when the editor closes.
    var atts by remember { mutableStateOf<List<Attachment>>(emptyList()) }
    var removed by remember { mutableStateOf<List<Attachment>>(emptyList()) }
    LaunchedEffect(Unit) { if (initial != null) atts = withContext(Dispatchers.IO) { Repo.attachments(initial.id) } }
    fun addFrom(load: () -> Attachment?) {
        scope.launch {
            val a = withContext(Dispatchers.IO) { load() }
            if (a != null) atts = atts + a else Toast.makeText(ctx, "没能读取这个文件", Toast.LENGTH_SHORT).show()
        }
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) addFrom { Attachments.import(ctx, uri) }
    }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) addFrom { Attachments.import(ctx, uri) }
    }
    var cameraFile by remember { mutableStateOf<File?>(null) }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = cameraFile
        if (ok && f != null) addFrom { Attachments.importCamera(ctx, f) } else f?.delete()
        cameraFile = null
    }

    fun close() {
        Attachments.discard(atts)
        onDone()
    }

    fun save() {
        val cents = Money.parse(amount)
        if (cents == null) { error = "请填金额，例如 12.5"; return }
        val cat = category.ifBlank { noteGuess ?: initial?.category?.takeUnless { it.isBlank() } ?: "其他" }
        val tx = (initial ?: Tx(type = type, amountCents = cents, time = time, channel = "手动")).copy(
            type = type, amountCents = cents, category = cat, note = note.trim(), time = time, status = Status.OK,
        )
        saving = true
        scope.launch {
            withContext(Dispatchers.IO) {
                val id = if (initial == null) Repo.insert(tx) else { Repo.update(tx); initial.id }
                atts.filter { it.id == 0L }.forEach { Repo.addAttachment(it.copy(txId = id)) }
                removed.forEach { Repo.deleteAttachment(it) }
            }
            Bubble.refresh()
            onDone()
        }
    }

    Dialog(
        onDismissRequest = ::close,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        BackHandler(onBack = ::close)
        // The dialog window has its own system bars: dark icons on our light background (light ones in dark mode).
        val view = androidx.compose.ui.platform.LocalView.current
        val dark = androidx.compose.foundation.isSystemInDarkTheme()
        androidx.compose.runtime.SideEffect {
            (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window?.let { w ->
                androidx.core.view.WindowCompat.getInsetsController(w, view).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
        }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
                Row(Modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(::close) { Icon(Icons.Filled.Close, "关闭") }
                    Text(
                        when {
                            initial == null -> "记一笔"
                            initial.amountCents == null -> "补上金额"
                            else -> "修改"
                        },
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
                    )
                    if (initial != null) IconButton({ confirmDelete = true }) { Icon(Icons.Filled.DeleteOutline, "删除", tint = ExpenseRed) }
                }

                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                    if (initial != null && initial.source != "manual") {
                        Text(
                            listOf(initial.channel, Kinds.label(initial.kind), initial.counterpart, initial.raw)
                                .filter { it.isNotBlank() }.distinct().joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                        )
                    }
                    TypeToggle(type) { if (it != type) { type = it; category = "" } }

                    // amount
                    Spacer(Modifier.height(12.dp))
                    SoftCard(padding = 4.dp) {
                        OutlinedTextField(
                            value = amount, onValueChange = { amount = it; error = null },
                            placeholder = { Text("0.00", style = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold)) },
                            prefix = { Text("¥ ", style = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold)) },
                            textStyle = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, color = if (type == TxType.INCOME) IncomeGreen else MaterialTheme.colorScheme.onSurface),
                            singleLine = true, isError = error != null,
                            supportingText = error?.let { { Text(it) } },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color.Transparent, focusedBorderColor = Color.Transparent, errorBorderColor = Color.Transparent),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    // category
                    GroupLabel("分类", if (category.isEmpty()) "可不选" else "")
                    SoftCard(padding = 12.dp) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (c in categories) FilterChip(
                                selected = c == category,
                                onClick = { category = if (category == c) "" else c },
                                label = { Text(CategoryStyle.label(c)) },
                            )
                        }
                    }

                    // note
                    GroupLabel("备注")
                    SoftCard(padding = 12.dp) {
                        OutlinedTextField(
                            value = note, onValueChange = { note = it },
                            placeholder = { Text("这笔钱花在哪了，如：奶茶、午饭") },
                            singleLine = true, shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedBorderColor = Color.Transparent,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        val chips = recentNotes.filter { it != note.trim() }
                        if (chips.isNotEmpty()) Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            for (n in chips) Text(
                                n, style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.secondaryContainer)
                                    .clickable { note = n }.padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                        if (category.isEmpty() && noteGuess != null) Text(
                            "不选分类的话，按备注记到「$noteGuess」",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                        )
                    }

                    // time
                    GroupLabel("时间")
                    SoftCard(padding = 0.dp) {
                        SettingRow(
                            Icons.Filled.Schedule, Tint.Blue,
                            SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.CHINA).format(Date(time)),
                            trailing = { Text("改时间", color = MaterialTheme.colorScheme.primary) },
                        ) {
                            val cal = Calendar.getInstance().apply { timeInMillis = time }
                            DatePickerDialog(ctx, { _, y, m, d ->
                                TimePickerDialog(ctx, { _, h, min ->
                                    cal.set(y, m, d, h, min, 0)
                                    time = cal.timeInMillis
                                }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
                            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
                        }
                    }

                    // receipts
                    GroupLabel("凭证", "收据照片、发票，可不加")
                    SoftCard(padding = 12.dp) {
                        if (atts.isNotEmpty()) {
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                for (a in atts) AttachmentTile(a, onOpen = { Attachments.open(ctx, a) }) {
                                    if (a.id == 0L) Attachments.discard(listOf(a)) else removed = removed + a
                                    atts = atts - a
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AddButton(Icons.Filled.PhotoCamera, "拍照", Modifier.weight(1f)) {
                                val (f, uri) = Attachments.cameraTarget(ctx)
                                cameraFile = f
                                runCatching { takePhoto.launch(uri) }.onFailure { Toast.makeText(ctx, "没找到相机", Toast.LENGTH_SHORT).show() }
                            }
                            AddButton(Icons.Filled.PhotoLibrary, "相册", Modifier.weight(1f)) {
                                pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }
                            AddButton(Icons.Filled.UploadFile, "文件", Modifier.weight(1f)) {
                                pickFile.launch(arrayOf("application/pdf", "image/*"))
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                Button(
                    ::save, enabled = !saving,
                    colors = ButtonDefaults.buttonColors(containerColor = Brand),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).height(52.dp),
                ) { Text("保存", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
            }
        }

        if (confirmDelete && initial != null) AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除这笔账？") },
            text = { Text(if (atts.isNotEmpty()) "它的 ${atts.size} 个凭证也会一起删掉。" else "删掉就找不回来了。") },
            confirmButton = {
                TextButton({
                    confirmDelete = false
                    Attachments.discard(atts)
                    scope.launch {
                        withContext(Dispatchers.IO) { Repo.delete(initial.id) }
                        Bubble.refresh()
                        onDone()
                    }
                }) { Text("删除", color = ExpenseRed) }
            },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun AddButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun AttachmentTile(a: Attachment, onOpen: () -> Unit, onRemove: () -> Unit) {
    val px = with(LocalDensity.current) { 76.dp.roundToPx() }
    val thumb by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, a.file) {
        if (a.isImage) value = withContext(Dispatchers.IO) { runCatching { Attachments.thumbnail(a, px) }.getOrNull() }
    }
    Box(Modifier.padding(top = 6.dp, end = 6.dp)) {
        Box(
            Modifier.size(76.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onOpen),
            contentAlignment = Alignment.Center,
        ) {
            val t = thumb
            if (t != null) Image(t, a.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(6.dp)) {
                Icon(Icons.Filled.Description, null, tint = ExpenseRed, modifier = Modifier.size(28.dp))
                Text(a.name, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Box(
            Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-6).dp).size(22.dp).clip(CircleShape)
                .background(Color(0xCC1C1A24)).clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Close, "移除", tint = Color.White, modifier = Modifier.size(14.dp)) }
    }
}
