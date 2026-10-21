package com.zpb.rearmusic

import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.Intent
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private var appsRevision by mutableIntStateOf(0)
    private var requestedApps = false
    private val appsPermission = "com.android.permission.GET_INSTALLED_APPS"
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        appsRevision++
    }

    override fun onResume() {
        super.onResume()
        appsRevision++
    }

    private fun canReadApps(): Boolean = try {
        packageManager.getPermissionInfo(appsPermission, 0)
        checkSelfPermission(appsPermission) == PackageManager.PERMISSION_GRANTED
    } catch (_: PackageManager.NameNotFoundException) { true }

    private fun requestApps() {
        if (requestedApps && !shouldShowRequestPermissionRationale(appsPermission)) {
            startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        } else {
            requestedApps = true
            permissionLauncher.launch(appsPermission)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.isNavigationBarContrastEnforced = false
        setContent {
            MiuixTheme(colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                MusicSettings()
            }
        }
    }

    private data class App(val label: String, val pkg: String, val system: Boolean, val icon: ImageBitmap)

    @Composable
    private fun MusicSettings() {
        val prefs = remember { Settings.prefs(this) }
        var revision by remember { mutableIntStateOf(0) }
        DisposableEffect(prefs) {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> revision++ }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }
        val selected = remember(revision) { prefs.getStringSet("apps", emptySet())!!.toSet() }
        var query by rememberSaveable { mutableStateOf("") }
        var showSystem by rememberSaveable { mutableStateOf(false) }
        var apps by remember { mutableStateOf<List<App>>(emptyList()) }
        var loading by remember { mutableStateOf(true) }
        var loadError by remember { mutableStateOf(false) }
        val authorized = remember(appsRevision) { canReadApps() }
        LaunchedEffect(appsRevision) {
            if (!authorized) { apps = emptyList(); loading = false; return@LaunchedEffect }
            loading = true
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    packageManager.getInstalledApplications(0).filter {
                        it.packageName != packageName &&
                            (packageManager.getLaunchIntentForPackage(it.packageName) != null || it.packageName in selected)
                    }.map {
                        val icon = it.loadIcon(packageManager)
                        val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
                        icon.setBounds(0, 0, 96, 96)
                        icon.draw(Canvas(bitmap))
                        App(it.loadLabel(packageManager).toString(), it.packageName,
                            it.flags and ApplicationInfo.FLAG_SYSTEM != 0, bitmap.asImageBitmap())
                    }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
                }
            }
            apps = result.getOrDefault(emptyList())
            loadError = result.isFailure
            loading = false
        }
        val visible = remember(apps, query, showSystem, selected) {
            apps.filter { (showSystem || !it.system || it.pkg in selected) &&
                (it.label.contains(query, true) || it.pkg.contains(query, true)) }
                .sortedByDescending { it.pkg in selected }
        }
        Scaffold(topBar = {
            SmallTopAppBar(title = "RearMusic", actions = {
                Row(Modifier.padding(end = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("系统应用", style = MiuixTheme.textStyles.footnote1)
                    Spacer(Modifier.width(8.dp))
                    Switch(checked = showSystem, onCheckedChange = { showSystem = it },
                        modifier = Modifier.semantics { contentDescription = "显示系统应用" })
                }
            })
        }) { padding ->
            LazyColumn(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()).imePadding(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp,
                    bottom = padding.calculateBottomPadding() + 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Text("小米 17 Pro / Pro Max · 背屏音乐", Modifier.padding(top = 8.dp, bottom = 4.dp),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
                item {
                    Card(insideMargin = PaddingValues(20.dp)) {
                        Text("模块状态", style = MiuixTheme.textStyles.headline1)
                        Spacer(Modifier.height(10.dp))
                        Text(prefs.getString("status_whitelist", "白名单：尚未注入")!!,
                            style = MiuixTheme.textStyles.footnote1)
                        Spacer(Modifier.height(6.dp))
                        Text(prefs.getString("status_systemui", "走马灯：尚未注入")!!,
                            style = MiuixTheme.textStyles.footnote1)
                        Spacer(Modifier.height(12.dp))
                        Text("在 LSPosed 中启用模块，勾选背屏中心、系统界面，然后重启手机。",
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    }
                }
                item {
                    Card(insideMargin = PaddingValues(horizontal = 20.dp, vertical = 4.dp)) {
                        SettingSwitch("音乐控件白名单", "让选中的播放器进入背屏音乐空间", "whitelist", true, prefs, revision)
                        SettingSwitch("音频走马灯", "随真实音频律动，暂停或熄屏时隐藏", "lights", true, prefs, revision)
                        SettingSwitch("切歌强制刷新", "音乐信息不更新时开启", "force_update", false, prefs, revision)
                    }
                }
                item {
                    Card(insideMargin = PaddingValues(20.dp)) {
                        SettingSlider("灯条高度", "height", 24f..72f, 40, prefs)
                        Spacer(Modifier.height(14.dp))
                        SettingSlider("距离屏幕底部", "bottom", 0f..100f, 8, prefs)
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("白名单应用", style = MiuixTheme.textStyles.title3, modifier = Modifier.weight(1f))
                        Text("已选 ${selected.size}", style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.primary)
                    }
                }
                item {
                    TextField(value = query, onValueChange = { query = it }, label = "搜索应用或包名",
                        useLabelAsPlaceholder = true, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                if (!authorized) item {
                    Card(insideMargin = PaddingValues(20.dp)) {
                        Text("需要应用列表权限，才能选择播放器。", style = MiuixTheme.textStyles.footnote1)
                        Spacer(Modifier.height(12.dp))
                        TextButton("授权应用列表", onClick = { requestApps() }, modifier = Modifier.fillMaxWidth())
                    }
                }
                items(visible, key = { it.pkg }) { app ->
                    val checked = app.pkg in selected
                    fun select(value: Boolean) {
                        val next = selected.toMutableSet()
                        if (value) next.add(app.pkg) else next.remove(app.pkg)
                        prefs.edit().putStringSet("apps", next).apply()
                        Settings.changed(this@MainActivity)
                    }
                    Card(onClick = { select(!checked) }, insideMargin = PaddingValues(16.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Image(app.icon, null, Modifier.size(44.dp))
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(app.pkg, style = MiuixTheme.textStyles.footnote1,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Spacer(Modifier.width(12.dp))
                            Checkbox(state = if (checked) ToggleableState.On else ToggleableState.Off,
                                onClick = { select(!checked) })
                        }
                    }
                }
                if (visible.isEmpty() && authorized) item {
                    Text(when { loading -> "正在加载应用…"; loadError -> "读取应用列表失败，请在系统设置中允许获取应用列表。"; else -> "没有匹配的应用" },
                        Modifier.padding(16.dp), style = MiuixTheme.textStyles.footnote1)
                }
            }
        }
    }

    @Composable
    private fun SettingSwitch(title: String, description: String, key: String, fallback: Boolean,
                              prefs: SharedPreferences, revision: Int) {
        val checked = remember(revision) { prefs.getBoolean(key, fallback) }
        Row(Modifier.fillMaxWidth().heightIn(min = 76.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 16.dp)) {
                Text(title)
                Spacer(Modifier.height(4.dp))
                Text(description, style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
            Switch(checked = checked, onCheckedChange = {
                prefs.edit().putBoolean(key, it).apply()
                Settings.changed(this@MainActivity)
            }, modifier = Modifier.semantics { contentDescription = title })
        }
    }

    @Composable
    private fun SettingSlider(title: String, key: String, range: ClosedFloatingPointRange<Float>,
                              fallback: Int, prefs: SharedPreferences) {
        var value by remember { mutableFloatStateOf(prefs.getInt(key, fallback).toFloat()) }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f))
            Text("${value.roundToInt()} dp", style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
        Spacer(Modifier.height(12.dp))
        Slider(value = value, onValueChange = { value = it }, valueRange = range,
            onValueChangeFinished = {
                prefs.edit().putInt(key, value.roundToInt()).apply()
                Settings.changed(this)
            }, modifier = Modifier.fillMaxWidth().semantics { contentDescription = title })
    }
}
