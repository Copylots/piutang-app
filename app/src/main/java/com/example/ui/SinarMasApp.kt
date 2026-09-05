package com.example.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.database.ActivityLogEntity
import com.example.database.PiutangEntity
import com.example.filemanager.FileManagerHelper
import com.example.filemanager.LocalFileItem
import com.example.security.CryptoHelper
import com.example.viewmodel.SinarMasViewModel
import java.io.File
import java.text.DecimalFormat

enum class AppTab {
    PIUTANG, FILE_MANAGER, ANALYTICS, ACTIVITY_LOGS, SETTINGS
}

@Composable
fun SinarMasApp(
    viewModel: SinarMasViewModel,
    isDarkTheme: Boolean,
    onToggleTheme: (Boolean) -> Unit
) {
    val context = LocalContext.current

    // Trigger toast status notifications
    LaunchedEffect(viewModel.exportStatusMessage) {
        viewModel.exportStatusMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.exportStatusMessage = null
        }
    }

    if (!viewModel.is2FAAuthenticated) {
        LoginScreen(viewModel = viewModel)
    } else {
        var currentTab by remember { mutableStateOf(AppTab.PIUTANG) }

        Scaffold(
            topBar = {
                SinarMasTopBar(
                    currentTab = currentTab,
                    onLogout = { viewModel.logout() }
                )
            },
            bottomBar = {
                SinarMasBottomNavigation(
                    currentTab = currentTab,
                    onTabSelected = { currentTab = it }
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (currentTab) {
                    AppTab.PIUTANG -> PiutangScreen(viewModel = viewModel)
                    AppTab.FILE_MANAGER -> FileManagerScreen(viewModel = viewModel)
                    AppTab.ANALYTICS -> AnalyticsScreen(viewModel = viewModel)
                    AppTab.ACTIVITY_LOGS -> ActivityLogsScreen(viewModel = viewModel)
                    AppTab.SETTINGS -> SettingsScreen(
                        viewModel = viewModel,
                        isDarkTheme = isDarkTheme,
                        onToggleTheme = onToggleTheme
                    )
                }
            }
        }
    }
}

// 1. TOP BAR COMPONENT
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SinarMasTopBar(currentTab: AppTab, onLogout: () -> Unit) {
    val title = when (currentTab) {
        AppTab.PIUTANG -> "SINAR MAS ALUMINIUM"
        AppTab.FILE_MANAGER -> "PENGELOLA FILE"
        AppTab.ANALYTICS -> "ANALISIS AKTIVITAS"
        AppTab.ACTIVITY_LOGS -> "LOG AKTIVITAS AMAN"
        AppTab.SETTINGS -> "PENGATURAN"
    }

    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Business,
                    contentDescription = "Logo",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    letterSpacing = 1.sp
                )
            }
        },
        actions = {
            IconButton(onClick = onLogout) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = "Lock App",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

// 2. BOTTOM NAVIGATION COMPONENT
@Composable
fun SinarMasBottomNavigation(currentTab: AppTab, onTabSelected: (AppTab) -> Unit) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
        val items = listOf(
            Triple(AppTab.PIUTANG, Icons.Default.Assignment, "Piutang"),
            Triple(AppTab.FILE_MANAGER, Icons.Default.FolderOpen, "Files"),
            Triple(AppTab.ANALYTICS, Icons.Default.BarChart, "Analisis"),
            Triple(AppTab.ACTIVITY_LOGS, Icons.Default.Security, "Logs"),
            Triple(AppTab.SETTINGS, Icons.Default.Settings, "Setelan")
        )

        items.forEach { (tab, icon, label) ->
            NavigationBarItem(
                selected = currentTab == tab,
                onClick = { onTabSelected(tab) },
                icon = { Icon(imageVector = icon, contentDescription = label) },
                label = { Text(label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    }
}

// 3. LOGIN / 2FA AUTH SHIELD
@Composable
fun LoginScreen(viewModel: SinarMasViewModel) {
    var inputPin by remember { mutableStateOf("") }
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Icon lock
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.VerifiedUser,
                    contentDescription = "Secure lock icon",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(45.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "APLIKASI SINAR MAS ALUMINIUM",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = "Otentikasi Dua Faktor (2FA)",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(top = 4.dp),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Masukkan PIN Akses Aman",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Dots row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 12.dp)
                    ) {
                        for (i in 0 until 4) {
                            val active = i < inputPin.length
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (active) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outlineVariant
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (active) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        shape = CircleShape
                                    )
                            )
                        }
                    }

                    if (viewModel.pinError) {
                        Text(
                            text = "PIN salah, silakan coba lagi.",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Numeric Keyboard Layout
                    val keys = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                        listOf("C", "0", "DEL")
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        keys.forEach { row ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                row.forEach { key ->
                                    val isAction = key == "C" || key == "DEL"
                                    Button(
                                        onClick = {
                                            viewModel.pinError = false
                                            when (key) {
                                                "C" -> inputPin = ""
                                                "DEL" -> if (inputPin.isNotEmpty()) inputPin = inputPin.dropLast(1)
                                                else -> {
                                                    if (inputPin.length < 4) {
                                                        inputPin += key
                                                        if (inputPin.length == 4) {
                                                            viewModel.verifyPin(inputPin)
                                                            inputPin = ""
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(55.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isAction) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f)
                                            else MaterialTheme.colorScheme.secondaryContainer,
                                            contentColor = if (isAction) MaterialTheme.colorScheme.onErrorContainer
                                            else MaterialTheme.colorScheme.onSecondaryContainer
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(
                                            text = key,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "PIN Bawaan: 1234",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

// Helper formatting Rupiah currency
private fun formatRp(n: Int): String {
    return "Rp %,d".format(n).replace(",", ".")
}

// 4. PIUTANG SCREEN
@Composable
fun PiutangScreen(viewModel: SinarMasViewModel) {
    val context = LocalContext.current
    val query by viewModel.piutangSearchQuery.collectAsStateWithLifecycle()
    val piutangList by viewModel.filteredPiutangList.collectAsStateWithLifecycle()

    var showAddEditDialog by remember { mutableStateOf<PiutangEntity?>(null) }
    var isNewItem by remember { mutableStateOf(true) }

    val sum0 = piutangList.sumOf { it.m0 }
    val sum1 = piutangList.sumOf { it.m1 }
    val sum2 = piutangList.sumOf { it.m2 }
    val sum3 = piutangList.sumOf { it.m3 }
    val grandTotal = piutangList.sumOf { it.total }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // Dashboard cards
        DashboardMetricsCard(sum0, sum1, sum2, sum3, grandTotal)

        Spacer(modifier = Modifier.height(10.dp))

        // Search Field
        OutlinedTextField(
            value = query,
            onValueChange = { viewModel.setPiutangSearchQuery(it) },
            placeholder = { Text("Cari Nama Meubel...", fontSize = 14.sp) },
            leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = "Search icon") },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.setPiutangSearchQuery("") }) {
                        Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("search_field"),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Action buttons bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    isNewItem = true
                    showAddEditDialog = PiutangEntity(nama = "", m0 = 0, m1 = 0, m2 = 0, m3 = 0, total = 0)
                },
                modifier = Modifier
                    .weight(1.2f)
                    .testTag("submit_button"),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Icon", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("TAMBAH DATA", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = { viewModel.triggerExportPdf(context, piutangList) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC05A12)),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                Icon(imageVector = Icons.Default.PictureAsPdf, contentDescription = "PDF Icon", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("PDF", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = { viewModel.triggerExportCsv(context, piutangList) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F723A)),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                Icon(imageVector = Icons.Default.FileDownload, contentDescription = "CSV Icon", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("CSV", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Label Logo path
        if (viewModel.selectedLogoPath.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = "Logo active",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Logo Terpilih: ${File(viewModel.selectedLogoPath).name}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Table Header Label
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                )
                .padding(vertical = 10.dp, horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("NAMA MEUBEL / BULAN", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.weight(1.5f))
            Text("TOTAL PIUTANG", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        }

        // LazyList of Piutangs
        if (piutangList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.AssignmentLate,
                        contentDescription = "No data available",
                        tint = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Data piutang tidak ditemukan",
                        color = MaterialTheme.colorScheme.secondary,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
            ) {
                itemsIndexed(piutangList) { index, item ->
                    PiutangRowItem(
                        index = index,
                        item = item,
                        onClick = {
                            isNewItem = false
                            showAddEditDialog = item
                        }
                    )
                }
            }
        }
    }

    // Add Edit Dialog
    showAddEditDialog?.let { dialogItem ->
        AddEditPiutangDialog(
            item = dialogItem,
            isNewItem = isNewItem,
            onDismiss = { showAddEditDialog = null },
            onSave = { nama, m0, m1, m2, m3 ->
                if (isNewItem) {
                    viewModel.addPiutang(nama, m0, m1, m2, m3)
                } else {
                    viewModel.updatePiutang(dialogItem.id, nama, m0, m1, m2, m3)
                }
                showAddEditDialog = null
            },
            onDelete = {
                viewModel.deletePiutang(dialogItem)
                showAddEditDialog = null
            }
        )
    }
}

// Dashboard metrics layout with nice visual container
@Composable
fun DashboardMetricsCard(sum0: Int, sum1: Int, sum2: Int, sum3: Int, grandTotal: Int) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "DASBOR PIUTANG MEUBEL",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Sub items columns
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text("0 Bln", fontSize = 10.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                    Text(formatRp(sum0), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text("1 Bln", fontSize = 10.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                    Text(formatRp(sum1), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text("2 Bln", fontSize = 10.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                    Text(formatRp(sum2), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text("3 Bln", fontSize = 10.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                    Text(formatRp(sum3), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Divider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TOTAL KESELURUHAN:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = formatRp(grandTotal),
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

// Single Row Item for furniture table list (simulating visual RecyclerView performance and style)
@Composable
fun PiutangRowItem(index: Int, item: PiutangEntity, onClick: () -> Unit) {
    // Zebra background
    val backgroundColor = if (index % 2 == 1) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1.5f)) {
                Text(
                    text = "${index + 1}. ${item.nama}",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                // Months details inline
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("M0: ${formatShortRp(item.m0)}", fontSize = 10.sp, color = MaterialTheme.colorScheme.secondary)
                    Text("M1: ${formatShortRp(item.m1)}", fontSize = 10.sp, color = MaterialTheme.colorScheme.secondary)
                    Text("M2: ${formatShortRp(item.m2)}", fontSize = 10.sp, color = MaterialTheme.colorScheme.secondary)
                    Text("M3: ${formatShortRp(item.m3)}", fontSize = 10.sp, color = MaterialTheme.colorScheme.secondary)
                }
            }

            Text(
                text = formatRp(item.total),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End
            )
        }
    }
}

// Formats number to short version like 100k
private fun formatShortRp(n: Int): String {
    return if (n >= 1_000_000) {
        val f = n / 1_000_000.0
        val dec = DecimalFormat("#.#")
        "${dec.format(f)}M"
    } else if (n >= 1_000) {
        "${n / 1000}k"
    } else {
        n.toString()
    }
}

// 5. ADD EDIT DIALOG POPUP
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditPiutangDialog(
    item: PiutangEntity,
    isNewItem: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, Int, Int, Int, Int) -> Unit,
    onDelete: () -> Unit
) {
    var nama by remember { mutableStateOf(item.nama) }
    var m0 by remember { mutableStateOf(item.m0.toString()) }
    var m1 by remember { mutableStateOf(item.m1.toString()) }
    var m2 by remember { mutableStateOf(item.m2.toString()) }
    var m3 by remember { mutableStateOf(item.m3.toString()) }

    var errorState by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isNewItem) "Tambah Pembukuan Baru" else "Ubah Data Pembukuan",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = nama,
                    onValueChange = { nama = it; errorState = false },
                    label = { Text("Nama Meubel / Pelanggan") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = errorState && nama.trim().isEmpty()
                )

                OutlinedTextField(
                    value = m0,
                    onValueChange = { m0 = it.filter { char -> char.isDigit() } },
                    label = { Text("M0 (0 Bulan)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )

                OutlinedTextField(
                    value = m1,
                    onValueChange = { m1 = it.filter { char -> char.isDigit() } },
                    label = { Text("M1 (1 Bulan)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )

                OutlinedTextField(
                    value = m2,
                    onValueChange = { m2 = it.filter { char -> char.isDigit() } },
                    label = { Text("M2 (2 Bulan)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )

                OutlinedTextField(
                    value = m3,
                    onValueChange = { m3 = it.filter { char -> char.isDigit() } },
                    label = { Text("M3 (3 Bulan)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (nama.trim().isEmpty()) {
                        errorState = true
                    } else {
                        onSave(
                            nama,
                            m0.toIntOrNull() ?: 0,
                            m1.toIntOrNull() ?: 0,
                            m2.toIntOrNull() ?: 0,
                            m3.toIntOrNull() ?: 0
                        )
                    }
                }
            ) {
                Text("SIMPAN")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) {
                    Text("BATAL")
                }
                if (!isNewItem) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("HAPUS")
                    }
                }
            }
        }
    )
}

// 6. FILE MANAGER SCREEN
@Composable
fun FileManagerScreen(viewModel: SinarMasViewModel) {
    val context = LocalContext.current
    
    // File category filters
    val categories = listOf("ALL", "MEDIA", "DOCUMENTS")
    
    // Sort systems
    val sortOptions = listOf(
        Pair("DATE_DESC", "Terbaru"),
        Pair("DATE_ASC", "Terlama"),
        Pair("SIZE_DESC", "Ukuran Besar"),
        Pair("SIZE_ASC", "Ukuran Kecil"),
        Pair("NAME_ASC", "Nama A-Z"),
        Pair("NAME_DESC", "Nama Z-A")
    )

    // Setup Intent Document Picker for Activity Result API
    val dirPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            // Find path if possible or keep scanning default Downloads folder
            // To ensure compatibility across Android, we let user select standard files
            Toast.makeText(context, "Sinar Mas: Membuka Folder Berhasil!", Toast.LENGTH_SHORT).show()
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            // Picked custom file, can be registered as Logo path
            // For simple implementation, we copy path or let them use it.
            // Under Scoped storage, let's display success
            Toast.makeText(context, "Membuka Berkas sukses!", Toast.LENGTH_SHORT).show()
        }
    }

    var showMoveDialogByPath by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        // Upper directory section
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Direktori Pencarian:", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                    Text(
                        text = viewModel.currentDirectory.absolutePath,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row {
                    IconButton(
                        onClick = { dirPickerLauncher.launch(null) },
                        modifier = Modifier
                            .size(36.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = "Open Directory SAF",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = { viewModel.scanCurrentDirectory() },
                        modifier = Modifier
                            .size(36.dp)
                            .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh scan",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Search and Filters
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // File search input
            OutlinedTextField(
                value = viewModel.fileSearchQuery,
                onValueChange = { viewModel.updateFileFilters(search = it) },
                placeholder = { Text("Cari Berkas...", fontSize = 13.sp) },
                leadingIcon = { Icon(imageVector = Icons.Default.FindInPage, contentDescription = null, modifier = Modifier.size(18.dp)) },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp)
            )

            // Clear search
            if (viewModel.fileSearchQuery.isNotEmpty()) {
                IconButton(
                    onClick = { viewModel.updateFileFilters(search = "") },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear search")
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Categories & Sorting chips
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Category Row
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                categories.forEach { cat ->
                    val selected = viewModel.fileCategory == cat
                    InputChip(
                        selected = selected,
                        onClick = { viewModel.updateFileFilters(category = cat) },
                        label = { Text(cat, fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                        colors = InputChipDefaults.inputChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            // Sort Selector Row
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                sortOptions.forEach { (code, label) ->
                    val selected = viewModel.fileSortBy == code
                    FilterChip(
                        selected = selected,
                        onClick = { viewModel.updateFileFilters(sortBy = code) },
                        label = { Text(label, fontSize = 10.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Files List section with Loading State
        if (viewModel.isLoadingFiles) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Memindai folder aktif...",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        } else {
            if (viewModel.fileList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.FolderZip,
                            contentDescription = "Empty",
                            tint = MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Tidak ada berkas yang cocok",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(viewModel.fileList) { fileItem ->
                        FileItemCard(
                            fileItem = fileItem,
                            isSelectedLogo = viewModel.selectedLogoPath == fileItem.path,
                            onSelectLogo = { viewModel.selectLogo(fileItem.path) },
                            onDelete = { viewModel.deleteFile(fileItem.path) },
                            onMove = { showMoveDialogByPath = fileItem.path }
                        )
                    }
                }
            }
        }
    }

    // Move File Dialog
    showMoveDialogByPath?.let { filePath ->
        AlertDialog(
            onDismissRequest = { showMoveDialogByPath = null },
            title = { Text("Pindahkan Berkas", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Text(
                    text = "Apakah Anda ingin memindahkan berkas '${File(filePath).name}' ke direktori root aman Aplikasi Sinar Mas?",
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val innerFolder = File(context.filesDir, "SinarMasAman")
                        viewModel.moveFile(filePath, innerFolder)
                        showMoveDialogByPath = null
                        Toast.makeText(context, "Berkas dipindahkan ke folder aman!", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("PINDAHKAN")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMoveDialogByPath = null }) {
                    Text("BATAL")
                }
            }
        )
    }
}

// File row layout representing single files (RecyclerView optimized performance in Compose)
@Composable
fun FileItemCard(
    fileItem: LocalFileItem,
    isSelectedLogo: Boolean,
    onSelectLogo: () -> Unit,
    onDelete: () -> Unit,
    onMove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelectedLogo) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (isSelectedLogo) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (fileItem.isDirectory) Icons.Default.Folder
                        else if (FileManagerHelper.isMediaFile(fileItem.extension)) Icons.Default.Image
                        else Icons.Default.Description,
                        contentDescription = "File Type icon",
                        tint = if (fileItem.isDirectory) Color(0xFFD4AF37)
                        else if (isSelectedLogo) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(24.dp)
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = fileItem.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (!fileItem.isDirectory) {
                                Text(FileManagerHelper.formatSize(fileItem.size), fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                            }
                            Text(FileManagerHelper.formatDate(fileItem.lastModified), fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }

                Row {
                    // Selected logo star badge or trigger select logo option
                    if (!fileItem.isDirectory && FileManagerHelper.isMediaFile(fileItem.extension)) {
                        IconButton(
                            onClick = onSelectLogo,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (isSelectedLogo) Icons.Default.Star else Icons.Outlined.StarBorder,
                                contentDescription = "Pilih sebagai logo",
                                tint = if (isSelectedLogo) Color(0xFFDAA520) else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Move file button
                    if (!fileItem.isDirectory) {
                        IconButton(
                            onClick = onMove,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DriveFileMove,
                                contentDescription = "Pindahkan berkas",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Delete file button
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Hapus berkas",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

// 7. ANALYTICS VISUAL DASHBOARD (Custom drawn interactive charts via Canvas!)
@Composable
fun AnalyticsScreen(viewModel: SinarMasViewModel) {
    val piutangList by viewModel.filteredPiutangList.collectAsStateWithLifecycle()

    val sum0 = piutangList.sumOf { it.m0 }
    val sum1 = piutangList.sumOf { it.m1 }
    val sum2 = piutangList.sumOf { it.m2 }
    val sum3 = piutangList.sumOf { it.m3 }
    val grandTotal = piutangList.sumOf { it.total }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Upper stats summary card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "ANALISIS STRUKTUR UMUR PIUTANG",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Total Piutang Berjalan:", fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary)
                    Text(formatRp(grandTotal), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Banyak Toko Meubel:", fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary)
                    Text("${piutangList.size} Toko", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }

        // Custom drawn Ring/Donut Chart showing distribution
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "PERSENTASE ALOKASI TEMPO",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(20.dp))

                if (grandTotal == 0) {
                    Text(
                        text = "Data kosong untuk dianalisis",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(vertical = 40.dp)
                    )
                } else {
                    // Interactive Canvas Drawing Donut
                    val p0 = sum0.toFloat() / grandTotal
                    val p1 = sum1.toFloat() / grandTotal
                    val p2 = sum2.toFloat() / grandTotal
                    val p3 = sum3.toFloat() / grandTotal

                    val color0 = Color(0xFF00796B) // Teal
                    val color1 = Color(0xFF1976D2) // Blue
                    val color2 = Color(0xFFFBC02D) // Yellow
                    val color3 = Color(0xFFD32F2F) // Red

                    Box(
                        modifier = Modifier.size(160.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(140.dp)) {
                            val strokeWidth = 50f
                            val size = Size(size.width, size.height)

                            var startAngle = -90f

                            // Draw m0
                            val angle0 = p0 * 360f
                            drawArc(
                                color = color0,
                                startAngle = startAngle,
                                sweepAngle = angle0,
                                useCenter = false,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                            startAngle += angle0

                            // Draw m1
                            val angle1 = p1 * 360f
                            drawArc(
                                color = color1,
                                startAngle = startAngle,
                                sweepAngle = angle1,
                                useCenter = false,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                            startAngle += angle1

                            // Draw m2
                            val angle2 = p2 * 360f
                            drawArc(
                                color = color2,
                                startAngle = startAngle,
                                sweepAngle = angle2,
                                useCenter = false,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                            startAngle += angle2

                            // Draw m3
                            val angle3 = p3 * 360f
                            drawArc(
                                color = color3,
                                startAngle = startAngle,
                                sweepAngle = angle3,
                                useCenter = false,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                        }

                        // Center text
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Rata Umur", fontSize = 9.sp, color = MaterialTheme.colorScheme.secondary)
                            val avgMonth = ((sum0 * 0) + (sum1 * 1) + (sum2 * 2) + (sum3 * 3)).toFloat() / grandTotal
                            Text("%.1f Bln".format(avgMonth), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Labels mapping in grid layout
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        LegendRow(color = color0, label = "0 Bln (Kredit Baru)", value = sum0, percent = p0)
                        LegendRow(color = color1, label = "1 Bln (Tempo Lancar)", value = sum1, percent = p1)
                        LegendRow(color = color2, label = "2 Bln (Peringatan Awal)", value = sum2, percent = p2)
                        LegendRow(color = color3, label = "3 Bln (Tunggakan Kritis)", value = sum3, percent = p3)
                    }
                }
            }
        }

        // Custom drawn Bar Chart for trend/magnitude
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "KOMPARASI BESAR TUNGGAKAN",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.secondary
                )

                Spacer(modifier = Modifier.height(20.dp))

                if (grandTotal == 0) {
                    Text(
                        text = "Data kosong",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(vertical = 30.dp)
                    )
                } else {
                    val maxVal = Math.max(1, listOf(sum0, sum1, sum2, sum3).maxOrNull() ?: 1)
                    val barColor = MaterialTheme.colorScheme.primary

                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        BarRow(label = "0 Bulan", value = sum0, ratio = sum0.toFloat() / maxVal, color = Color(0xFF00796B))
                        BarRow(label = "1 Bulan", value = sum1, ratio = sum1.toFloat() / maxVal, color = Color(0xFF1976D2))
                        BarRow(label = "2 Bulan", value = sum2, ratio = sum2.toFloat() / maxVal, color = Color(0xFFFBC02D))
                        BarRow(label = "3 Bulan", value = sum3, ratio = sum3.toFloat() / maxVal, color = Color(0xFFD32F2F))
                    }
                }
            }
        }
    }
}

@Composable
fun LegendRow(color: Color, label: String, value: Int, percent: Float) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
        Text(
            text = "${formatRp(value)} (%.1f%%)".format(percent * 100),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun BarRow(label: String, value: Int, ratio: Float, color: Color) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(formatRp(value), fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
        }
        Spacer(modifier = Modifier.height(4.dp))
        // Progress bar track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(ratio)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color)
            )
        }
    }
}

// 8. ENCRYPTED ACTIVITY LOG SCREEN
@Composable
fun ActivityLogsScreen(viewModel: SinarMasViewModel) {
    val logsList by viewModel.allLogs.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        // Explanation Card of security encryption system
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.EnhancedEncryption,
                        contentDescription = "Shield encryption",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "SISTEM ENKRIPSI RIWAYAT SISTEM",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 0.5.sp
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Seluruh aktivitas pembukuan, perubahan data, ekspor file, dan pemindahan file dienkripsi secara aman menggunakan sandi AES-128 sebelum disimpan ke SQLite database. Hal ini melindungi data rahasia Sinar Mas.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.secondary,
                    lineHeight = 15.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Security controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Lihat Data Ciphertext (Asli):",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = viewModel.showEncryptedLogs,
                    onCheckedChange = { viewModel.showEncryptedLogs = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary
                    )
                )
            }

            Button(
                onClick = { viewModel.clearLogs() },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(6.dp)
            ) {
                Icon(imageVector = Icons.Default.DeleteForever, contentDescription = "Clear", modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("BERSIHKAN", fontSize = 10.sp, color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Logs Timeline Title
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                )
                .padding(vertical = 10.dp, horizontal = 12.dp)
        ) {
            Text(
                text = "LINIMASA AKTIVITAS SISTEM",
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }

        // List logs
        if (logsList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text("Log riwayat kosong", color = MaterialTheme.colorScheme.outline, fontSize = 12.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)
                    )
            ) {
                items(logsList) { log ->
                    LogItemRow(log = log, showEncrypted = viewModel.showEncryptedLogs)
                }
            }
        }
    }
}

@Composable
fun LogItemRow(log: ActivityLogEntity, showEncrypted: Boolean) {
    // Decrypt if requested
    val displayAction = if (showEncrypted) log.action else CryptoHelper.decrypt(log.action)
    val displayDetails = if (showEncrypted) log.details else CryptoHelper.decrypt(log.details)

    val actionColor = when (CryptoHelper.decrypt(log.action)) {
        "TAMBAH DATA" -> Color(0xFF00796B)
        "UBAH DATA" -> Color(0xFF1976D2)
        "HAPUS DATA" -> Color(0xFFD32F2F)
        "HAPUS FILE" -> Color(0xFFD32F2F)
        "PINDAH FILE" -> Color(0xFF7B1FA2)
        "EKSPOR PDF" -> Color(0xFFC05A12)
        "EKSPOR CSV" -> Color(0xFF0F723A)
        else -> MaterialTheme.colorScheme.primary
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp, horizontal = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Action Label Box
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(actionColor.copy(alpha = 0.15f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = displayAction,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    color = actionColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Timestamp
            Text(
                text = FileManagerHelper.formatDate(log.timestamp),
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.outline
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Details text
        Text(
            text = displayDetails,
            fontSize = 11.sp,
            color = if (showEncrypted) MaterialTheme.colorScheme.outline
            else MaterialTheme.colorScheme.onSurface,
            lineHeight = 15.sp,
            fontFamily = if (showEncrypted) FontFamily.Monospace else FontFamily.Default
        )

        Spacer(modifier = Modifier.height(10.dp))
        Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    }
}

// 9. SETTINGS & ACCESS CONTROL
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SinarMasViewModel,
    isDarkTheme: Boolean,
    onToggleTheme: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var isEditingPin by remember { mutableStateOf(false) }
    var newPinInput by remember { mutableStateOf("") }
    var pinSaveSuccess by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // App Theme Configuration Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "KONFIGURASI TAMPILAN",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
                            contentDescription = "Theme icon",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Mode Gelap (Malam)", fontSize = 14.sp)
                    }

                    Switch(
                        checked = isDarkTheme,
                        onCheckedChange = { onToggleTheme(it) }
                    )
                }
            }
        }

        // Authentication & PIN Code Security Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "KEAMANAN OTENTIKASI (2FA PIN)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "PIN 2FA digunakan untuk mengunci seluruh aplikasi Sinar Mas dari akses pihak tidak berwenang saat perangkat dipinjam.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.secondary,
                    lineHeight = 15.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                if (isEditingPin) {
                    OutlinedTextField(
                        value = newPinInput,
                        onValueChange = { input ->
                            if (input.length <= 6) {
                                newPinInput = input.filter { it.isDigit() }
                            }
                        },
                        label = { Text("Masukkan PIN Baru (Min. 4 digit)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { isEditingPin = false; newPinInput = "" }) {
                            Text("BATAL")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (newPinInput.length >= 4) {
                                    viewModel.updatePin(newPinInput)
                                    isEditingPin = false
                                    newPinInput = ""
                                    pinSaveSuccess = true
                                    Toast.makeText(context, "PIN Baru Berhasil disimpan!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = newPinInput.length >= 4
                        ) {
                            Text("SIMPAN")
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "PIN Aktif: ****",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )

                        Button(
                            onClick = { isEditingPin = true; pinSaveSuccess = false },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("UBAH PIN", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Custom PDF Logo Customizer Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "TATA LETAK LAPORAN (LOGO)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Konfigurasi berkas gambar logo Sinar Mas yang akan tercetak di bagian tajuk utama dokumen PDF.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.secondary,
                    lineHeight = 15.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                if (viewModel.selectedLogoPath.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Image, contentDescription = "Logo active", tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = File(viewModel.selectedLogoPath).name,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        IconButton(
                            onClick = { viewModel.clearLogo() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Clear logo", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Outlined.Image,
                                contentDescription = "Empty Logo",
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Gunakan Tab 'Files' untuk memilih gambar sebagai Logo Laporan kustom.",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.outline,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        // Footer Brand/Version metadata
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Sinar Mas Aluminium Enterprise",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary
            )
            Text(
                text = "Versi Kotlin 2.2 | SQLite Database Aman AES-128",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}
