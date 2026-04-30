package com.example.myapplication

import android.app.TimePickerDialog
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.google.firebase.Firebase
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.database
import com.google.firebase.database.PropertyName
import com.google.firebase.storage.storage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

enum class ComplaintStatus {
    PENDING, IN_PROGRESS, RESOLVED
}

data class ContactInfo(
    val id: String = UUID.randomUUID().toString(),
    val label: String = "",
    val value: String = "",
    val type: ContactType = ContactType.PHONE
)

enum class ContactType {
    PHONE, EMAIL, OTHER
}

data class UserProfile(
    val name: String = "",
    val phone: String = "",
    val houseNumber: String = "",
    val userType: String = "", // Owner or Tenant
    val registrationDate: Long = System.currentTimeMillis()
)

data class AppNotification(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val message: String = "",
    val targetHouse: String? = null,
    val isForAdmin: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    @get:PropertyName("isRead") @set:PropertyName("isRead") var isRead: Boolean = false
)

data class Complaint(
    val id: Int = 0,
    val complaintNumber: String = "",
    val houseNumber: String = "",
    val cellNumber: String = "",
    val description: String = "",
    var status: ComplaintStatus = ComplaintStatus.PENDING,
    var lastUpdated: Long = System.currentTimeMillis(),
    var rating: Int = 0 // 0 means no rating yet
)

data class Announcement(
    val id: Int = 0,
    val title: String = "",
    val content: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

data class WaterSchedule(
    val id: String = UUID.randomUUID().toString(),
    val valveNumber: String = "",
    val dateMillis: Long = 0L,
    val openTime: String = "",
    val closeTime: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

class SocietyViewModel : ViewModel() {
    private val database = Firebase.database.reference
    
    private val _complaints = mutableStateListOf<Complaint>()
    val complaints: List<Complaint> get() = _complaints

    private val _announcements = mutableStateListOf<Announcement>()
    val announcements: List<Announcement> get() = _announcements

    private val _waterSchedules = mutableStateListOf<WaterSchedule>()
    val waterSchedules: List<WaterSchedule> get() = _waterSchedules

    private val _contacts = mutableStateListOf<ContactInfo>()
    val contacts: List<ContactInfo> get() = _contacts

    private val _registeredUsers = mutableStateListOf<UserProfile>()
    val registeredUsers: List<UserProfile> get() = _registeredUsers
    
    private val _notifications = mutableStateListOf<AppNotification>()
    val notifications: List<AppNotification> get() = _notifications
    
    private var nextComplaintId = 1
    private var nextAnnouncementId = 1
    
    var adminPassword by mutableStateOf("admin")
        private set

    var superAdminPassword by mutableStateOf("superadmin")
        private set

    var notificationMessage by mutableStateOf<String?>(null)
        private set

    var isRefreshing by mutableStateOf(false)
        private set

    init {
        observeFirebaseData()
    }

    private fun observeFirebaseData() {
        // Observe Settings (Passwords) - Seed if empty
        database.child("settings").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val remoteAdmin = snapshot.child("adminPassword").getValue(String::class.java)
                if (remoteAdmin == null) {
                    database.child("settings").child("adminPassword").setValue("admin")
                } else {
                    adminPassword = remoteAdmin
                }
                
                val remoteSuper = snapshot.child("superAdminPassword").getValue(String::class.java)
                if (remoteSuper == null) {
                    database.child("settings").child("superAdminPassword").setValue("superadmin")
                } else {
                    superAdminPassword = remoteSuper
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        // Observe Complaints
        database.child("complaints").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                _complaints.clear()
                snapshot.children.forEach { child ->
                    child.getValue(Complaint::class.java)?.let { _complaints.add(it) }
                }
                nextComplaintId = (_complaints.maxOfOrNull { it.id } ?: 0) + 1
            }
            override fun onCancelled(error: DatabaseError) { Log.e("Firebase", error.message) }
        })

        // Observe Announcements
        database.child("announcements").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                _announcements.clear()
                snapshot.children.forEach { child ->
                    child.getValue(Announcement::class.java)?.let { _announcements.add(0, it) }
                }
                nextAnnouncementId = (_announcements.maxOfOrNull { it.id } ?: 0) + 1
            }
            override fun onCancelled(error: DatabaseError) { Log.e("Firebase", error.message) }
        })

        // Observe Water Schedules
        database.child("waterSchedules").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                _waterSchedules.clear()
                snapshot.children.forEach { child ->
                    child.getValue(WaterSchedule::class.java)?.let { _waterSchedules.add(it) }
                }
                _waterSchedules.sortByDescending { it.timestamp }
            }
            override fun onCancelled(error: DatabaseError) { Log.e("Firebase", error.message) }
        })

        // Observe Contacts
        database.child("contacts").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                _contacts.clear()
                if (!snapshot.exists()) {
                    val initialContacts = listOf(
                        ContactInfo(label = "Admin Email", value = "zahidmgsk1@gmail.com", type = ContactType.EMAIL),
                        ContactInfo(label = "Security", value = "0300-XXXXXXX", type = ContactType.PHONE),
                        ContactInfo(label = "Office", value = "021-XXXXXXX", type = ContactType.PHONE)
                    )
                    initialContacts.forEach { addContact(it.label, it.value, it.type) }
                } else {
                    snapshot.children.forEach { child ->
                        child.getValue(ContactInfo::class.java)?.let { _contacts.add(it) }
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) { Log.e("Firebase", error.message) }
        })

        // Observe Registered Users
        database.child("users").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                _registeredUsers.clear()
                snapshot.children.forEach { child ->
                    child.getValue(UserProfile::class.java)?.let { _registeredUsers.add(it) }
                }
            }
            override fun onCancelled(error: DatabaseError) { Log.e("Firebase", error.message) }
        })

        // Observe Notifications
        database.child("notifications").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                _notifications.clear()
                snapshot.children.forEach { child ->
                    child.getValue(AppNotification::class.java)?.let { _notifications.add(0, it) }
                }
            }
            override fun onCancelled(error: DatabaseError) { Log.e("Firebase", error.message) }
        })
    }

    fun registerUser(name: String, phone: String, houseNumber: String, userType: String) {
        val user = UserProfile(name, phone, houseNumber, userType)
        database.child("users").child(phone).setValue(user)
        
        val notification = AppNotification(
            title = "New User Registered", 
            message = "$name from house $houseNumber has joined.", 
            isForAdmin = true
        )
        database.child("notifications").push().setValue(notification)
    }

    private fun addNotificationToFirebase(title: String, message: String, house: String? = null, forAdmin: Boolean = false) {
        val notification = AppNotification(title = title, message = message, targetHouse = house, isForAdmin = forAdmin)
        database.child("notifications").push().setValue(notification)
        notificationMessage = message
    }

    fun markNotificationAsRead(notificationId: String) {
        database.child("notifications").orderByChild("id").equalTo(notificationId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                snapshot.children.firstOrNull()?.ref?.child("isRead")?.setValue(true)
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    fun markAllNotificationsAsRead(isAdmin: Boolean, userHouse: String) {
        database.child("notifications").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                snapshot.children.forEach { child ->
                    val notification = child.getValue(AppNotification::class.java)
                    if (notification != null && !notification.isRead) {
                        if (isAdmin || (notification.targetHouse == null || notification.targetHouse == userHouse)) {
                            child.ref.child("isRead").setValue(true)
                        }
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    fun changeAdminPassword(newPassword: String) {
        if (newPassword.isNotBlank()) {
            database.child("settings").child("adminPassword").setValue(newPassword)
            notificationMessage = "Admin password updated"
        }
    }

    fun changeSuperAdminPassword(newPassword: String) {
        if (newPassword.isNotBlank()) {
            database.child("settings").child("superAdminPassword").setValue(newPassword)
            notificationMessage = "Super Admin password updated"
        }
    }

    fun deleteComplaint(id: Int) {
        database.child("complaints").child(id.toString()).removeValue()
        notificationMessage = "Complaint deleted"
    }

    fun deleteAnnouncement(id: Int) {
        database.child("announcements").child(id.toString()).removeValue()
        notificationMessage = "Announcement deleted"
    }

    fun deleteUser(phone: String) {
        database.child("users").child(phone).removeValue()
        notificationMessage = "User deleted"
    }

    fun deleteWaterSchedule(id: String) {
        database.child("waterSchedules").child(id).removeValue()
        notificationMessage = "Schedule deleted"
    }

    fun addComplaint(houseNumber: String, cellNumber: String, description: String): String {
        val complaintNumber = "CMP-${UUID.randomUUID().toString().take(6).uppercase()}"
        val complaint = Complaint(nextComplaintId, complaintNumber, houseNumber, cellNumber, description)
        database.child("complaints").child(nextComplaintId.toString()).setValue(complaint)
        
        addNotificationToFirebase("New Complaint Alert", "House $houseNumber filed $complaintNumber", houseNumber, true)
        notificationMessage = "New Complaint $complaintNumber filed successfully!"
        return complaintNumber
    }

    fun updateStatus(id: Int, newStatus: ComplaintStatus) {
        database.child("complaints").child(id.toString()).child("status").setValue(newStatus)
        database.child("complaints").child(id.toString()).child("lastUpdated").setValue(System.currentTimeMillis())
        
        val complaint = _complaints.find { it.id == id }
        complaint?.let {
            addNotificationToFirebase("Complaint Updated", "Your complaint ${it.complaintNumber} is now ${newStatus.name}", it.houseNumber)
        }
    }

    fun submitRating(id: Int, rating: Int) {
        database.child("complaints").child(id.toString()).child("rating").setValue(rating)
        val complaint = _complaints.find { it.id == id }
        complaint?.let {
            val notification = AppNotification(
                title = "Resident Feedback", 
                message = "House ${it.houseNumber} rated ${it.complaintNumber}: $rating stars", 
                targetHouse = it.houseNumber, 
                isForAdmin = true
            )
            database.child("notifications").push().setValue(notification)
            notificationMessage = "Thank you for your feedback!"
        }
    }

    fun addAnnouncement(title: String, content: String) {
        val announcement = Announcement(nextAnnouncementId, title, content)
        database.child("announcements").child(nextAnnouncementId.toString()).setValue(announcement)
        addNotificationToFirebase("Announcement", "New Announcement: $title")
    }

    fun addWaterSchedule(valveNumber: String, dateMillis: Long, openTime: String, closeTime: String) {
        val schedule = WaterSchedule(
            id = UUID.randomUUID().toString(),
            valveNumber = valveNumber,
            dateMillis = dateMillis,
            openTime = openTime,
            closeTime = closeTime,
            timestamp = System.currentTimeMillis()
        )
        val date = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(dateMillis))
        database.child("waterSchedules").child(schedule.id).setValue(schedule)
        addNotificationToFirebase("Schedule Update", "Water schedule for $valveNumber on $date has been added")
    }

    fun addContact(label: String, value: String, type: ContactType) {
        val contact = ContactInfo(label = label, value = value, type = type)
        database.child("contacts").child(contact.id).setValue(contact)
        notificationMessage = "Contact '$label' added"
    }

    fun removeContact(contact: ContactInfo) {
        database.child("contacts").child(contact.id).removeValue()
    }

    fun getComplaints(complaintNumber: String, houseNumber: String): List<Complaint> {
        return _complaints.filter { 
            (complaintNumber.isBlank() || it.complaintNumber.equals(complaintNumber.trim(), ignoreCase = true)) &&
            (houseNumber.isBlank() || it.houseNumber.equals(houseNumber.trim(), ignoreCase = true))
        }
    }

    fun refreshData() {
        viewModelScope.launch {
            isRefreshing = true
            delay(1500)
            isRefreshing = false
            notificationMessage = "Data refreshed"
        }
    }

    fun clearNotification() {
        notificationMessage = null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComplaintPortalApp(viewModel: SocietyViewModel = viewModel()) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Request Notification Permission for Android 13+
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(context, "Notifications are disabled.", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    
    // Global listener for notifications
    LaunchedEffect(viewModel.notificationMessage) {
        viewModel.notificationMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearNotification()
        }
    }

    var isRegistered by remember { 
        mutableStateOf(
            !sharedPrefs.getString("user_name", null).isNullOrBlank() && 
            !sharedPrefs.getString("user_house", null).isNullOrBlank()
        )
    }
    
    var isAdminMode by remember { mutableStateOf(false) }
    var isSuperAdminMode by remember { mutableStateOf(false) }
    var showLoginDialog by remember { mutableStateOf(false) }
    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var showNotificationCenter by remember { mutableStateOf(false) }
    var passwordInput by remember { mutableStateOf("") }
    
    var selectedTab by remember { mutableIntStateOf(0) }

    var showWelcomeScreen by remember { mutableStateOf(true) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (!isRegistered) {
            RegistrationScreen { name, phone, house, type ->
                sharedPrefs.edit()
                    .putString("user_name", name)
                    .putString("user_phone", phone)
                    .putString("user_house", house)
                    .putString("user_type", type)
                    .apply()
                viewModel.registerUser(name, phone, house, type)
                isRegistered = true
            }
        } else if (showWelcomeScreen) {
            WelcomeScreen(onEnter = { showWelcomeScreen = false })
        } else {
            if (showNotificationCenter) {
                val registeredHouse = sharedPrefs.getString("user_house", "") ?: ""
                NotificationCenterDialog(
                    notifications = viewModel.notifications,
                    isAdmin = isAdminMode || isSuperAdminMode,
                    userHouse = registeredHouse,
                    onMarkRead = { viewModel.markNotificationAsRead(it) },
                    onMarkAllRead = { viewModel.markAllNotificationsAsRead(isAdminMode || isSuperAdminMode, registeredHouse) },
                    onDismiss = { showNotificationCenter = false }
                )
            }

            if (showLoginDialog) {
                AlertDialog(
                    onDismissRequest = { showLoginDialog = false },
                    title = { Text("Admin Login") },
                    text = {
                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = { passwordInput = it },
                            label = { Text("Enter Password") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true
                        )
                    },
                    confirmButton = {
                        Button(onClick = {
                            if (passwordInput == viewModel.superAdminPassword) {
                                isSuperAdminMode = true
                                isAdminMode = false
                                showLoginDialog = false
                                passwordInput = ""
                            } else if (passwordInput == viewModel.adminPassword) {
                                isAdminMode = true
                                isSuperAdminMode = false
                                showLoginDialog = false
                                passwordInput = ""
                            } else {
                                Toast.makeText(context, "Invalid Password", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Text("Login")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showLoginDialog = false; passwordInput = "" }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            if (showChangePasswordDialog) {
                var newAdminPass by remember { mutableStateOf("") }
                var newSuperPass by remember { mutableStateOf("") }
                
                AlertDialog(
                    onDismissRequest = { showChangePasswordDialog = false },
                    title = { Text(if (isSuperAdminMode) "Change Passwords" else "Change Admin Password") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = newAdminPass,
                                onValueChange = { newAdminPass = it },
                                label = { Text("New Admin Password") },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true
                            )
                            if (isSuperAdminMode) {
                                OutlinedTextField(
                                    value = newSuperPass,
                                    onValueChange = { newSuperPass = it },
                                    label = { Text("New Super Admin Password") },
                                    visualTransformation = PasswordVisualTransformation(),
                                    singleLine = true
                                )
                            }
                        }
                    },
                    confirmButton = {
                        Button(onClick = {
                            if (newAdminPass.isNotBlank()) {
                                viewModel.changeAdminPassword(newAdminPass)
                            }
                            if (isSuperAdminMode && newSuperPass.isNotBlank()) {
                                viewModel.changeSuperAdminPassword(newSuperPass)
                            }
                            showChangePasswordDialog = false
                        }) {
                            Text("Update")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showChangePasswordDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            Scaffold(
                modifier = Modifier.imePadding(),
                topBar = {
                    TopAppBar(
                        title = { 
                            Column {
                                val userName = sharedPrefs.getString("user_name", "Resident")
                                    Text("KN GGC", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    if (isSuperAdminMode) "⭐ Super Admin" 
                                    else if (isAdminMode) "🛠️ Admin Panel" 
                                    else "Hi, $userName", 
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSuperAdminMode) MaterialTheme.colorScheme.primary else Color.Unspecified
                                )
                            }
                        },
                        actions = {
                            IconButton(onClick = { showNotificationCenter = true }) {
                                BadgedBox(
                                    badge = { 
                                        val displayHouse = sharedPrefs.getString("user_house", "") ?: ""
                                        val unreadCount = if (isAdminMode || isSuperAdminMode) {
                                            viewModel.notifications.count { !it.isRead }
                                        } else {
                                            viewModel.notifications.count { !it.isRead && (!it.isForAdmin && (it.targetHouse == null || it.targetHouse == displayHouse)) }
                                        }
                                        if (unreadCount > 0) {
                                            Badge { Text(unreadCount.toString()) }
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.Notifications, contentDescription = "Notifications")
                                }
                            }
                            if (isAdminMode || isSuperAdminMode) {
                                IconButton(onClick = { showChangePasswordDialog = true }) {
                                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                                }
                                Button(
                                    onClick = { isAdminMode = false; isSuperAdminMode = false },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text("Logout", color = Color.White)
                                }
                            } else {
                                Button(
                                    onClick = { showLoginDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(Icons.Default.AdminPanelSettings, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Admin Access")
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = if (isSuperAdminMode) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer,
                            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                },
                bottomBar = {
                    NavigationBar {
                        NavigationBarItem(
                            icon = { Icon(Icons.Default.ReportProblem, contentDescription = null) },
                            label = { Text("Complaints") },
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 }
                        )
                        NavigationBarItem(
                            icon = { Icon(Icons.Default.WaterDrop, contentDescription = null) },
                            label = { Text("Water Schedule") },
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 }
                        )
                        NavigationBarItem(
                            icon = { Icon(Icons.Default.Campaign, contentDescription = null) },
                            label = { Text("Announcements") },
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 }
                        )
                    }
                }
            ) { innerPadding ->
                PullToRefreshBox(
                    isRefreshing = viewModel.isRefreshing,
                    onRefresh = { viewModel.refreshData() },
                    modifier = Modifier.padding(innerPadding).fillMaxSize()
                ) {
                    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        when (selectedTab) {
                            0 -> if (isAdminMode || isSuperAdminMode) AdminComplaintScreen(viewModel, isSuperAdminMode) else UserComplaintScreen(viewModel)
                            1 -> WaterScheduleScreen(viewModel, isAdminMode || isSuperAdminMode, isSuperAdminMode)
                            2 -> AnnouncementScreen(viewModel, isAdminMode || isSuperAdminMode, isSuperAdminMode)
                        }
                    }
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)
        )
    }
}

@Composable
fun NotificationCenterDialog(
    notifications: List<AppNotification>,
    isAdmin: Boolean,
    userHouse: String,
    onMarkRead: (String) -> Unit,
    onMarkAllRead: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Notifications")
                TextButton(onClick = onMarkAllRead) {
                    Text("Mark all read", fontSize = 12.sp)
                }
            }
        },
        text = {
            val filteredNotifications = if (isAdmin) {
                notifications
            } else {
                notifications.filter { !it.isForAdmin && (it.targetHouse == null || it.targetHouse == userHouse) }
            }

            if (filteredNotifications.isEmpty()) {
                Text("No recent notifications.")
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(filteredNotifications) { notification ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (notification.isRead) Color.Transparent else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))
                                .clickable { onMarkRead(notification.id) }
                                .padding(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (notification.isForAdmin) Icons.Default.Warning else Icons.Default.Info,
                                    contentDescription = null,
                                    tint = if (notification.isForAdmin) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    notification.title, 
                                    fontWeight = if (notification.isRead) FontWeight.Normal else FontWeight.Bold, 
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (!notification.isRead) {
                                    Spacer(Modifier.weight(1f))
                                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                                }
                            }
                            Text(notification.message, style = MaterialTheme.typography.bodySmall)
                            Text(
                                SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(notification.timestamp)),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray
                            )
                            HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun RegistrationScreen(onRegister: (String, String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var houseNumber by remember { mutableStateOf("") }
    var userType by remember { mutableStateOf("Owner") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        ElevatedCard(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    Icons.Default.PersonAdd,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    "User Registration",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Please provide your details to continue",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Full Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                OutlinedTextField(
                    value = phone,
                    onValueChange = { input -> 
                        if (input.length <= 11 && input.all { it.isDigit() }) {
                            phone = input
                        }
                    },
                    label = { Text("Phone Number (11 digits)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                OutlinedTextField(
                    value = houseNumber,
                    onValueChange = { houseNumber = it },
                    label = { Text("House Number") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("e.g. A-123") }
                )

                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("I am a:", style = MaterialTheme.typography.labelLarge)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = userType == "Owner", onClick = { userType = "Owner" })
                        Text("Owner", modifier = Modifier.clickable { userType = "Owner" })
                        Spacer(Modifier.width(16.dp))
                        RadioButton(selected = userType == "Tenant", onClick = { userType = "Tenant" })
                        Text("Tenant", modifier = Modifier.clickable { userType = "Tenant" })
                    }
                }
                
                Button(
                    onClick = { 
                        if (name.isNotBlank() && phone.length == 11 && houseNumber.isNotBlank()) {
                            onRegister(name, phone, houseNumber, userType)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = name.isNotBlank() && phone.length == 11 && houseNumber.isNotBlank()
                ) {
                    Text("Register", fontSize = 18.sp)
                }
            }
        }
    }
}

@Composable
fun WelcomeScreen(onEnter: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp).fillMaxHeight()
        ) {
            Spacer(Modifier.weight(1f))
            Icon(
                Icons.Default.Eco, 
                contentDescription = null, 
                modifier = Modifier.size(100.dp), 
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(24.dp))
            Text(
                "Welcome to\nKN Gohar Green City", 
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Your Smart Residents Portal for Complaints, News, and Water Schedules.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
            Spacer(Modifier.height(48.dp))
            Button(
                onClick = onEnter,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Enter Portal", fontSize = 18.sp)
            }
            Spacer(Modifier.weight(1f))
            
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 16.dp)) {
                Text(
                    "Progressive Panel", 
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "(The well wishers of GGC)", 
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaterScheduleScreen(viewModel: SocietyViewModel, isAdmin: Boolean, isSuperAdmin: Boolean) {
    val context = LocalContext.current
    val datePickerState = rememberDatePickerState()
    var showAddScheduleDialog by remember { mutableStateOf(false) }

    // Dialog state
    var valveNumberInput by remember { mutableStateOf("") }
    var openTimeInput by remember { mutableStateOf("") }
    var closeTimeInput by remember { mutableStateOf("") }
    
    // Time Picker Logic
    val timeFormat = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val openTimePickerDialog = TimePickerDialog(
        context,
        { _, hour, minute ->
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, hour)
            cal.set(Calendar.MINUTE, minute)
            openTimeInput = timeFormat.format(cal.time)
        },
        Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
        Calendar.getInstance().get(Calendar.MINUTE),
        false // 12-hour format
    )
    val closeTimePickerDialog = TimePickerDialog(
        context,
        { _, hour, minute ->
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, hour)
            cal.set(Calendar.MINUTE, minute)
            closeTimeInput = timeFormat.format(cal.time)
        },
        Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
        Calendar.getInstance().get(Calendar.MINUTE),
        false // 12-hour format
    )

    // Use derived state to avoid recomposition on every scroll of the date picker
    val selectedDateMillis by remember {
        derivedStateOf {
            datePickerState.selectedDateMillis ?: System.currentTimeMillis()
        }
    }

    // Dialog for adding a schedule
    if (showAddScheduleDialog) {
        val dateForDialog = datePickerState.selectedDateMillis ?: System.currentTimeMillis()
        AlertDialog(
            onDismissRequest = { showAddScheduleDialog = false },
            title = { Text("Add Schedule for ${SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(dateForDialog))}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = valveNumberInput, 
                        onValueChange = { valveNumberInput = it }, 
                        label = { Text("Valve Number / Area") }
                    )
                    // Open Time Picker
                    Box(modifier = Modifier.clickable { openTimePickerDialog.show() }) {
                        OutlinedTextField(
                            value = openTimeInput, 
                            onValueChange = {}, 
                            label = { Text("Open Time") },
                            readOnly = true,
                            enabled = false, // To make it look like a button
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                disabledBorderColor = MaterialTheme.colorScheme.outline,
                                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    // Close Time Picker
                    Box(modifier = Modifier.clickable { closeTimePickerDialog.show() }) {
                        OutlinedTextField(
                            value = closeTimeInput, 
                            onValueChange = {}, 
                            label = { Text("Close Time") },
                            readOnly = true,
                            enabled = false, // To make it look like a button
                             colors = OutlinedTextFieldDefaults.colors(
                                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                disabledBorderColor = MaterialTheme.colorScheme.outline,
                                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (valveNumberInput.isNotBlank() && openTimeInput.isNotBlank() && closeTimeInput.isNotBlank()) {
                        viewModel.addWaterSchedule(valveNumberInput, dateForDialog, openTimeInput, closeTimeInput)
                        showAddScheduleDialog = false
                        // Reset fields
                        valveNumberInput = ""
                        openTimeInput = ""
                        closeTimeInput = ""
                    } else {
                        Toast.makeText(context, "Please fill all fields", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddScheduleDialog = false }) { Text("Cancel") }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text("Water Supply Calendar", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))

        // The Calendar View
        DatePicker(
            state = datePickerState,
            title = null,
            headline = null,
            showModeToggle = false,
            colors = DatePickerDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.3f)
            )
        )

        // Admin button to add a schedule for the selected date
        if(isAdmin) {
            Button(
                onClick = { 
                    // Reset time fields when opening the dialog
                    openTimeInput = ""
                    closeTimeInput = ""
                    showAddScheduleDialog = true 
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                enabled = datePickerState.selectedDateMillis != null
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                val buttonText = if(datePickerState.selectedDateMillis != null)
                    "Add Schedule for ${SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(selectedDateMillis))}"
                else "Select a date to add a schedule"
                Text(buttonText)
            }
        }

        HorizontalDivider(modifier = Modifier.padding(bottom = 12.dp))

        Text("Schedules for ${SimpleDateFormat("EEEE, dd MMM yyyy", Locale.getDefault()).format(Date(selectedDateMillis))}", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        // Get schedules for the selected day
        val schedulesForSelectedDate = remember(selectedDateMillis, viewModel.waterSchedules) {
            val cal1 = Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
            viewModel.waterSchedules.filter {
                val cal2 = Calendar.getInstance().apply { timeInMillis = it.dateMillis }
                cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
            }
        }

        if (schedulesForSelectedDate.isEmpty()) {
            Box(modifier=Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                 Text("No schedules for this date.", color=Color.Gray)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(schedulesForSelectedDate) { schedule ->
                    WaterScheduleCard(schedule, isSuperAdmin, onDelete = { viewModel.deleteWaterSchedule(schedule.id) })
                }
            }
        }
    }
}

@Composable
fun WaterScheduleCard(schedule: WaterSchedule, isSuperAdmin: Boolean, onDelete: () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.WaterDrop, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(text = "Valve: ${schedule.valveNumber}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Time: ${schedule.openTime} - ${schedule.closeTime}", 
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            if (isSuperAdmin) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                }
            }
        }
    }
}

@Composable
fun InfoChip(
    label: String, 
    icon: androidx.compose.ui.graphics.vector.ImageVector, 
    isSuperAdmin: Boolean = false,
    onDelete: () -> Unit = {}
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), 
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
            if (isSuperAdmin) {
                Icon(
                    Icons.Default.Close, 
                    contentDescription = "Remove", 
                    modifier = Modifier.size(14.dp).clickable { onDelete() },
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun UserComplaintScreen(viewModel: SocietyViewModel) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE) }
    val registeredHouse = sharedPrefs.getString("user_house", "") ?: ""
    val registeredPhone = sharedPrefs.getString("user_phone", "") ?: ""

    var description by remember { mutableStateOf("") }
    var trackCompNumber by remember { mutableStateOf("") }
    var trackedComplaints by remember { mutableStateOf(emptyList<Complaint>()) }
    var lastSubmittedNumber by remember { mutableStateOf<String?>(null) }
    var hasSearched by remember { mutableStateOf(false) }
    
    val clipboardManager = LocalClipboardManager.current

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Your Complaints (House: $registeredHouse)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = trackCompNumber, 
                        onValueChange = { trackCompNumber = it }, 
                        label = { Text("Complaint No. (Optional)") }, 
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("Filter by number") }
                    )
                }
                
                Button(
                    onClick = { 
                        trackedComplaints = viewModel.getComplaints(trackCompNumber, registeredHouse)
                        hasSearched = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { 
                    Icon(Icons.Default.Search, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("View My Complaints") 
                }

                if (trackedComplaints.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    trackedComplaints.forEach { complaint ->
                        var isCardVisible by remember { mutableStateOf(true) }
                        AnimatedVisibility(
                            visible = isCardVisible,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut() + slideOutHorizontally()
                        ) {
                            ComplaintCard(complaint, onRatingSubmitted = { rating -> 
                                viewModel.submitRating(complaint.id, rating)
                                isCardVisible = false
                            })
                        }
                    }
                } else if (hasSearched) {
                    Text("No complaints found for your house.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Lodge New Complaint", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                
                OutlinedTextField(
                    value = registeredHouse, 
                    onValueChange = {}, 
                    label = { Text("House Number") }, 
                    modifier = Modifier.fillMaxWidth(),
                    enabled = false,
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                
                OutlinedTextField(
                    value = registeredPhone, 
                    onValueChange = {}, 
                    label = { Text("Cell Number") }, 
                    modifier = Modifier.fillMaxWidth(),
                    enabled = false,
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                
                OutlinedTextField(
                    value = description, 
                    onValueChange = { description = it }, 
                    label = { Text("Problem Description") }, 
                    modifier = Modifier.fillMaxWidth(), 
                    minLines = 3
                )
                
                Button(
                    onClick = {
                        if (description.isNotBlank()) {
                            lastSubmittedNumber = viewModel.addComplaint(registeredHouse, registeredPhone, description)
                            description = ""
                        }
                    },
                    modifier = Modifier.align(Alignment.End)
                ) { Text("Submit Complaint") }
            }
        }

        lastSubmittedNumber?.let { num ->
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Success! Complaint registered:", style = MaterialTheme.typography.labelSmall)
                        Text("Reference: $num", fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = {
                        clipboardManager.setText(AnnotatedString(num))
                        Toast.makeText(context, "Reference Copied", Toast.LENGTH_SHORT).show()
                    }) { Icon(Icons.Default.ContentCopy, contentDescription = null) }
                }
            }
        }
    }
}

@Composable
fun AdminComplaintScreen(viewModel: SocietyViewModel, isSuperAdmin: Boolean) {
    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Active Complaints", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        items(viewModel.complaints) { complaint ->
            AdminComplaintCard(complaint, isSuperAdmin, onDelete = { viewModel.deleteComplaint(complaint.id) }) { status -> 
                viewModel.updateStatus(complaint.id, status) 
            }
        }
    }
}

@Composable
fun AnnouncementScreen(viewModel: SocietyViewModel, isAdmin: Boolean, isSuperAdmin: Boolean) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("New Announcement") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") })
                    OutlinedTextField(value = content, onValueChange = { content = it }, label = { Text("Message") }, minLines = 3)
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (title.isNotBlank() && content.isNotBlank()) {
                        viewModel.addAnnouncement(title, content)
                        showAddDialog = false; title = ""; content = ""
                    }
                }) { Text("Post") }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("Cancel") } }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { 
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Campaign, contentDescription = null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Society Announcements", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            }
            items(viewModel.announcements) { announcement ->
                AnnouncementCard(announcement, isSuperAdmin, onDelete = { viewModel.deleteAnnouncement(announcement.id) })
            }
        }
        if (isAdmin) {
            FloatingActionButton(onClick = { showAddDialog = true }, modifier = Modifier.align(Alignment.BottomEnd)) {
                Icon(Icons.Default.Add, contentDescription = "Add Announcement")
            }
        }
    }
}

@Composable
fun AnnouncementCard(announcement: Announcement, isSuperAdmin: Boolean, onDelete: () -> Unit) {
    val date = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(announcement.timestamp))
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Default.Campaign, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(announcement.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(date, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Spacer(modifier = Modifier.height(8.dp))
                Text(announcement.content, style = MaterialTheme.typography.bodyMedium)
            }
            if (isSuperAdmin) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                }
            }
        }
    }
}

@Composable
fun ComplaintCard(complaint: Complaint, onRatingSubmitted: (Int) -> Unit = {}) {
    var tempRating by remember { mutableIntStateOf(complaint.rating) }
    val statusColor = when (complaint.status) {
        ComplaintStatus.PENDING -> Color(0xFFE57373)
        ComplaintStatus.IN_PROGRESS -> Color(0xFF64B5F6)
        ComplaintStatus.RESOLVED -> Color(0xFF81C784)
    }
    
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(6.dp)
                    .background(statusColor)
            )
            
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = complaint.complaintNumber,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    StatusBadge(complaint.status)
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "House: ${complaint.houseNumber}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), thickness = 0.5.dp)
                
                Text(
                    text = complaint.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (complaint.status == ComplaintStatus.RESOLVED) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "How was the resolution?",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                repeat(5) { index ->
                                    val ratingValue = index + 1
                                    val isSelected = ratingValue <= (if (complaint.rating > 0) complaint.rating else tempRating)
                                    Icon(
                                        imageVector = if (isSelected) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                        contentDescription = null,
                                        tint = if (isSelected) Color(0xFFFFB300) else Color.Gray,
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clickable(enabled = complaint.rating == 0) { tempRating = ratingValue }
                                    )
                                }
                            }
                            
                            if (complaint.rating == 0 && tempRating > 0) {
                                Button(
                                    onClick = { onRatingSubmitted(tempRating) },
                                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp),
                                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 4.dp)
                                ) {
                                    Text("Submit Feedback", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminComplaintCard(complaint: Complaint, isSuperAdmin: Boolean, onDelete: () -> Unit, onStatusChange: (ComplaintStatus) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val statusColor = when (complaint.status) {
        ComplaintStatus.PENDING -> Color(0xFFE57373)
        ComplaintStatus.IN_PROGRESS -> Color(0xFF64B5F6)
        ComplaintStatus.RESOLVED -> Color(0xFF81C784)
    }

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(6.dp)
                    .background(statusColor)
            )
            
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = complaint.complaintNumber,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Home, null, modifier = Modifier.size(14.dp), tint = Color.Gray)
                            Spacer(Modifier.width(4.dp))
                            Text("House: ${complaint.houseNumber}", style = MaterialTheme.typography.labelSmall)
                        }
                        if (complaint.cellNumber.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Person, null, modifier = Modifier.size(14.dp), tint = Color.Gray)
                                Spacer(Modifier.width(4.dp))
                                Text("Contact: ${complaint.cellNumber}", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box {
                            AssistChip(
                                onClick = { expanded = true },
                                label = { Text(complaint.status.name, fontSize = 11.sp) },
                                trailingIcon = { Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(16.dp)) },
                                colors = AssistChipDefaults.assistChipColors(labelColor = statusColor)
                            )
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                ComplaintStatus.entries.forEach { status ->
                                    DropdownMenuItem(
                                        text = { Text(status.name) },
                                        onClick = { onStatusChange(status); expanded = false }
                                )
                                }
                            }
                        }
                        if (isSuperAdmin) {
                            IconButton(onClick = onDelete) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = complaint.description,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                
                if (complaint.rating > 0) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text("Resident Feedback: ", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        repeat(5) { index ->
                            Icon(
                                imageVector = if (index < complaint.rating) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                contentDescription = null,
                                tint = if (index < complaint.rating) Color(0xFFFFB300) else Color.Gray,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatusBadge(status: ComplaintStatus) {
    val color = when (status) {
        ComplaintStatus.PENDING -> Color(0xFFE57373)
        ComplaintStatus.IN_PROGRESS -> Color(0xFF64B5F6)
        ComplaintStatus.RESOLVED -> Color(0xFF81C784)
    }
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Text(
            text = status.name,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.5.sp
        )
    }
}
