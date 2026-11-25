package ir.iranos.manager

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.work.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

data class Customer(val name: String, val username: String, val optedIn: Boolean)
data class Campaign(val title: String, val body: String, val createdAt: String)
data class SendLog(val date: String, val detail: String, val result: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannel(this)
        setContent { IranosApp() }
    }
}

private const val PREFS = "iranos_manager"
private const val CONTACTS = "contacts_json"
private const val LOGS = "logs_json"
private const val CAMPAIGNS = "campaigns_json"
private const val REMINDER = "daily_reminder"
private const val REMINDER_HOUR = "reminder_hour"
private const val REMINDER_MIN = "reminder_min"

fun createNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel("iranos_daily", "یادآور ایرانوس", NotificationManager.IMPORTANCE_DEFAULT)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}

class DailyReminderWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(applicationContext, "iranos_daily")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("یادآور تبلیغات ایرانوس")
            .setContentText("پیام تبلیغاتی امروز را آماده و بازبینی کن.")
            .setAutoCancel(true).build()
        nm.notify(1202, notification)
        return Result.success()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IranosApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var tab by remember { mutableIntStateOf(0) }
    var title by remember { mutableStateOf("معرفی محصول جدید ایرانوس") }
    var body by remember { mutableStateOf("برای اطلاع از موجودی و قیمت محصولات روشنایی ایرانوس پیام بدهید.") }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var name by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var accepted by remember { mutableStateOf(false) }
    var reminderEnabled by remember { mutableStateOf(prefs.getBoolean(REMINDER, false)) }
    var reminderHour by remember { mutableIntStateOf(prefs.getInt(REMINDER_HOUR, 10)) }
    var reminderMin by remember { mutableIntStateOf(prefs.getInt(REMINDER_MIN, 0)) }
    var contacts by remember { mutableStateOf(loadContacts(prefs)) }
    var campaigns by remember { mutableStateOf(loadCampaigns(prefs)) }
    var logs by remember { mutableStateOf(loadLogs(prefs)) }
    var notice by remember { mutableStateOf<String?>(null) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            imageUri = uri
        }
    }
    val backupExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            val backup = JSONObject().put("contacts", contactsToJson(contacts)).put("campaigns", campaignsToJson(campaigns)).put("logs", logsToJson(logs)).toString(2)
            context.contentResolver.openOutputStream(uri)?.use { it.write(backup.toByteArray()) }
            notice = "پشتیبان ذخیره شد."
        }
    }
    val backupImporter = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) try {
            val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
            val root = JSONObject(raw)
            contacts = jsonToContacts(root.optJSONArray("contacts") ?: JSONArray())
            campaigns = jsonToCampaigns(root.optJSONArray("campaigns") ?: JSONArray())
            logs = jsonToLogs(root.optJSONArray("logs") ?: JSONArray())
            saveContacts(prefs, contacts); saveCampaigns(prefs, campaigns); saveLogs(prefs, logs)
            notice = "پشتیبان بازیابی شد."
        } catch (_: Exception) { notice = "فایل پشتیبان معتبر نیست." }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    MaterialTheme(colorScheme = lightColorScheme(primary = androidx.compose.ui.graphics.Color(0xFFB3261E), secondary = androidx.compose.ui.graphics.Color(0xFF263238))) {
        Scaffold(
            topBar = { TopAppBar(title = { Column { Text("ایرانوس", fontWeight = FontWeight.Bold); Text("مدیریت پیام‌های تلگرام", fontSize = 12.sp) } },
                actions = { Icon(Icons.Default.Lightbulb, null, Modifier.padding(end = 16.dp)) }) },
            bottomBar = {
                NavigationBar {
                    val labels = listOf("خانه", "مخاطبان", "پیام", "یادآور", "گزارش", "پشتیبان")
                    val icons = listOf(Icons.Default.Dashboard, Icons.Default.People, Icons.Default.Edit, Icons.Default.Schedule, Icons.Default.Assessment, Icons.Default.Backup)
                    labels.forEachIndexed { i, label -> NavigationBarItem(tab == i, { tab = i }, icon = { Icon(icons[i], null) }, label = { Text(label) }) }
                }
            }
        ) { padding ->
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                when (tab) {
                    0 -> HomeScreen(contacts.count { it.optedIn }, campaigns.size, logs.size, { tab = 2 }, Modifier.padding(padding))
                    1 -> CustomersScreen(contacts, name, { name = it }, username, { username = it }, accepted, { accepted = it },
                        { if (name.isNotBlank() && username.isNotBlank() && accepted) {
                            contacts = contacts + Customer(name.trim(), username.trim(), true); saveContacts(prefs, contacts)
                            name = ""; username = ""; accepted = false; notice = "مخاطب ثبت شد."
                        } }, { c -> contacts = contacts.filterNot { it == c }; saveContacts(prefs, contacts) }, Modifier.padding(padding))
                    2 -> CampaignScreen(title, { title = it }, body, { body = it }, imageUri, { imagePicker.launch(arrayOf("image/*")) },
                        onSave = {
                            campaigns = listOf(Campaign(title.trim(), body.trim(), nowString())) + campaigns
                            campaigns = campaigns.take(50); saveCampaigns(prefs, campaigns)
                            notice = "پیام در آرشیو ذخیره شد."
                        },
                        onShareText = {
                            val full = "$title\n\n$body\n\nپخش روشنایی ایرانوس\n09156554383\nwww.risesara.ir"
                            val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, full) }
                            context.startActivity(Intent.createChooser(intent, "اشتراک‌گذاری پیام"))
                            logs = (listOf(SendLog(nowString(), "اشتراک‌گذاری متن تبلیغ", "آماده شد")) + logs).take(100); saveLogs(prefs, logs)
                        },
                        onShareImage = {
                            imageUri?.let { uri ->
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "image/*"; putExtra(Intent.EXTRA_STREAM, uri)
                                    putExtra(Intent.EXTRA_TEXT, "$title\n\n$body\n\nپخش روشنایی ایرانوس\nwww.risesara.ir")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "اشتراک‌گذاری عکس و پیام"))
                                logs = (listOf(SendLog(nowString(), "اشتراک‌گذاری عکس و متن", "آماده شد")) + logs).take(100); saveLogs(prefs, logs)
                            }
                        }, onLoadCampaign = { c -> title = c.title; body = c.body; imageUri = null; notice = "پیام بارگذاری شد؛ عکس باید دوباره انتخاب شود." },
                        campaigns = campaigns, modifier = Modifier.padding(padding))
                    3 -> ReminderScreen(reminderEnabled, reminderHour, reminderMin, onToggle = { enabled ->
                        reminderEnabled = enabled; prefs.edit().putBoolean(REMINDER, enabled).apply()
                        updateReminder(context, enabled, reminderHour, reminderMin, permissionLauncher)
                    }, onTime = { h, m ->
                        reminderHour = h; reminderMin = m; prefs.edit().putInt(REMINDER_HOUR, h).putInt(REMINDER_MIN, m).apply()
                        if (reminderEnabled) updateReminder(context, true, h, m, permissionLauncher)
                    }, Modifier.padding(padding))
                    4 -> ReportsScreen(logs, Modifier.padding(padding))
                    else -> BackupScreen({ backupExporter.launch("iranos-backup-${System.currentTimeMillis()}.json") }, { backupImporter.launch(arrayOf("application/json", "text/*")) }, Modifier.padding(padding))
                }
            }
        }
        if (notice != null) {
            AlertDialog(onDismissRequest = { notice = null }, confirmButton = { TextButton(onClick = { notice = null }) { Text("باشه") } },
                title = { Text("پیام") }, text = { Text(notice ?: "") })
        }
    }
}

fun updateReminder(context: Context, enabled: Boolean, hour: Int, minute: Int, permissionLauncher: Any? = null) {
    val wm = WorkManager.getInstance(context)
    if (enabled) {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            // The UI separately requests permission on first activation.
        }
        val request = PeriodicWorkRequestBuilder<DailyReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(24, TimeUnit.HOURS).build()
        wm.enqueueUniquePeriodicWork("iranos_daily_reminder", ExistingPeriodicWorkPolicy.UPDATE, request)
    } else wm.cancelUniqueWork("iranos_daily_reminder")
}

fun nowString(): String = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date())

fun contactsToJson(items: List<Customer>): JSONArray = JSONArray().also { arr -> items.forEach { arr.put(JSONObject().put("name", it.name).put("username", it.username).put("optedIn", it.optedIn)) } }
fun campaignsToJson(items: List<Campaign>): JSONArray = JSONArray().also { arr -> items.forEach { arr.put(JSONObject().put("title", it.title).put("body", it.body).put("createdAt", it.createdAt)) } }
fun logsToJson(items: List<SendLog>): JSONArray = JSONArray().also { arr -> items.forEach { arr.put(JSONObject().put("date", it.date).put("detail", it.detail).put("result", it.result)) } }
fun jsonToContacts(arr: JSONArray): List<Customer> = (0 until arr.length()).map { arr.getJSONObject(it).let { o -> Customer(o.optString("name"), o.optString("username"), o.optBoolean("optedIn")) } }
fun jsonToCampaigns(arr: JSONArray): List<Campaign> = (0 until arr.length()).map { arr.getJSONObject(it).let { o -> Campaign(o.optString("title"), o.optString("body"), o.optString("createdAt")) } }
fun jsonToLogs(arr: JSONArray): List<SendLog> = (0 until arr.length()).map { arr.getJSONObject(it).let { o -> SendLog(o.optString("date"), o.optString("detail"), o.optString("result")) } }
fun loadContacts(p: android.content.SharedPreferences): List<Customer> = try { jsonToContacts(JSONArray(p.getString(CONTACTS, "[]"))) } catch (_: Exception) { emptyList() }
fun loadCampaigns(p: android.content.SharedPreferences): List<Campaign> = try { jsonToCampaigns(JSONArray(p.getString(CAMPAIGNS, "[]"))) } catch (_: Exception) { emptyList() }
fun loadLogs(p: android.content.SharedPreferences): List<SendLog> = try { jsonToLogs(JSONArray(p.getString(LOGS, "[]"))) } catch (_: Exception) { emptyList() }
fun saveContacts(p: android.content.SharedPreferences, list: List<Customer>) { p.edit().putString(CONTACTS, contactsToJson(list).toString()).apply() }
fun saveCampaigns(p: android.content.SharedPreferences, list: List<Campaign>) { p.edit().putString(CAMPAIGNS, campaignsToJson(list).toString()).apply() }
fun saveLogs(p: android.content.SharedPreferences, list: List<SendLog>) { p.edit().putString(LOGS, logsToJson(list).toString()).apply() }

@Composable
fun HomeScreen(approved: Int, campaignCount: Int, logCount: Int, onCompose: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("داشبورد ایرانوس", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("مخاطبان مجاز", approved.toString(), Modifier.weight(1f))
            StatCard("آرشیو پیام", campaignCount.toString(), Modifier.weight(1f))
            StatCard("گزارش", logCount.toString(), Modifier.weight(1f))
        }
        Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("تبلیغات روزانه", fontWeight = FontWeight.Bold)
            Text("پیام و تصویر را آماده کن؛ سپس از طریق اشتراک‌گذاری، در تلگرام بازبینی و ارسال دستی کن.")
            Button(onClick = onCompose, modifier = Modifier.fillMaxWidth()) { Text("ساخت پیام جدید") }
        } }
        Text("پخش روشنایی ایرانوس\n09156554383\nwww.risesara.ir")
    }
}
@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) { Column(Modifier.padding(10.dp)) { Text(label, fontSize = 11.sp); Text(value, fontSize = 23.sp, fontWeight = FontWeight.Bold) } }
}
@Composable
fun CustomersScreen(customers: List<Customer>, name: String, onName: (String) -> Unit, username: String, onUsername: (String) -> Unit,
    accepted: Boolean, onAccepted: (Boolean) -> Unit, onAdd: () -> Unit, onRemove: (Customer) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("مخاطبان رضایت‌داده", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        OutlinedTextField(name, onName, label = { Text("نام مشتری") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(username, onUsername, label = { Text("آیدی تلگرام") }, modifier = Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(accepted, onAccepted); Text("رضایت دریافت پیام تبلیغاتی ثبت شده") }
        Button(onClick = onAdd, enabled = name.isNotBlank() && username.isNotBlank() && accepted, modifier = Modifier.fillMaxWidth()) { Text("افزودن مخاطب") }
        HorizontalDivider()
        LazyColumn { items(customers) { c -> ListItem(headlineContent = { Text(c.name) }, supportingContent = { Text(c.username) },
            trailingContent = { IconButton(onClick = { onRemove(c) }) { Icon(Icons.Default.Delete, "حذف") } }) } }
    }
}
@Composable
fun CampaignScreen(title: String, onTitle: (String) -> Unit, body: String, onBody: (String) -> Unit, imageUri: Uri?, onPickImage: () -> Unit,
    onSave: () -> Unit, onShareText: () -> Unit, onShareImage: () -> Unit, onLoadCampaign: (Campaign) -> Unit,
    campaigns: List<Campaign>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("ساخت و آرشیو پیام", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        OutlinedTextField(title, onTitle, label = { Text("عنوان") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(body, onBody, label = { Text("متن تبلیغ") }, modifier = Modifier.fillMaxWidth().weight(1f), minLines = 4)
        OutlinedButton(onClick = onPickImage, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.AddPhotoAlternate, null); Text(if (imageUri == null) "انتخاب عکس از گالری" else "عکس انتخاب شد") }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = onSave, modifier = Modifier.weight(1f)) { Text("ذخیره پیام") }
            Button(onClick = onShareText, modifier = Modifier.weight(1f)) { Text("اشتراک متن") }
        }
        OutlinedButton(onClick = onShareImage, enabled = imageUri != null, modifier = Modifier.fillMaxWidth()) { Text("اشتراک عکس + متن") }
        Text("پیام‌های ذخیره‌شده", fontWeight = FontWeight.Bold)
        LazyColumn(modifier.heightIn(max = 130.dp)) { items(campaigns) { c -> ListItem(headlineContent = { Text(c.title) }, supportingContent = { Text(c.createdAt) },
            trailingContent = { TextButton(onClick = { onLoadCampaign(c) }) { Text("بارگذاری") } }) } }
    }
}
@Composable
fun ReminderScreen(enabled: Boolean, hour: Int, minute: Int, onToggle: (Boolean) -> Unit, onTime: (Int, Int) -> Unit, modifier: Modifier = Modifier) {
    var hourText by remember(hour) { mutableStateOf(hour.toString().padStart(2, '0')) }
    var minText by remember(minute) { mutableStateOf(minute.toString().padStart(2, '0')) }
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("یادآور تبلیغات", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("زمان پیشنهادی", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(hourText, { hourText = it.filter(Char::isDigit).take(2) }, label = { Text("ساعت") }, modifier = Modifier.weight(1f))
                OutlinedTextField(minText, { minText = it.filter(Char::isDigit).take(2) }, label = { Text("دقیقه") }, modifier = Modifier.weight(1f))
            }
            Button(onClick = {
                val h = hourText.toIntOrNull()?.coerceIn(0,23) ?: 10
                val m = minText.toIntOrNull()?.coerceIn(0,59) ?: 0
                onTime(h,m)
            }, modifier = Modifier.fillMaxWidth()) { Text("ذخیره زمان") }
            Row(verticalAlignment = Alignment.CenterVertically) { Text("فعال‌سازی یادآور", Modifier.weight(1f)); Switch(enabled, onToggle) }
        } }
        Text("اعلان برای آماده‌سازی پیام است، نه ارسال خودکار. زمان دقیق اعلان به محدودیت‌های اندروید و مدیریت باتری وابسته است.")
    }
}
@Composable
fun ReportsScreen(logs: List<SendLog>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("گزارش فعالیت", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("سوابق فقط آماده‌سازی یا اشتراک‌گذاری را ثبت می‌کند و وضعیت تحویل تلگرام را نشان نمی‌دهد.", fontSize = 12.sp)
        if (logs.isEmpty()) Text("گزارشی ثبت نشده.")
        LazyColumn { items(logs) { l -> ListItem(headlineContent = { Text(l.detail) }, supportingContent = { Text(l.date) }, trailingContent = { Text(l.result) }); HorizontalDivider() } }
    }
}
@Composable
fun BackupScreen(onExport: () -> Unit, onImport: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("پشتیبان‌گیری", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Card { Text("از مخاطبان، پیام‌های ذخیره‌شده و گزارش‌ها فایل JSON تهیه کن یا پشتیبان قبلی را بازیابی کن. فایل پشتیبان را محرمانه نگه دار.", Modifier.padding(16.dp)) }
        Button(onClick = onExport, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.SaveAlt, null); Text("خروجی گرفتن از پشتیبان") }
        OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Restore, null); Text("بازیابی پشتیبان") }
    }
}
