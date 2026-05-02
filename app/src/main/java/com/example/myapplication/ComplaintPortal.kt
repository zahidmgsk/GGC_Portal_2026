package com.example.myapplication

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ExitToApp
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
import androidx.compose.ui.res.painterResource
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
import com.example.myapplication.ui.theme.AppTheme
import com.example.myapplication.ui.theme.ComplaintPortalTheme
import com.example.myapplication.ui.theme.ThemeViewModel
import com.google.firebase.Firebase
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.GenericTypeIndicator
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.database
import com.google.firebase.database.PropertyName
import com.google.firebase.storage.storage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.material.icons.filled.SupervisorAccount
import androidx.core.content.FileProvider
import kotlin.system.exitProcess

enum class ComplaintStatus {
    PENDING, IN_PROGRESS, RESOLVED
}

enum class NotificationPriority {
    NORMAL, HIGH
}

data class Admin(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val password: String = ""
)

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
    val cnic: String = "",
    val houseNumber: String = "",
    val userType: String = "",
    val registrationDate: Long = System.currentTimeMillis(),
    @get:PropertyName("isApproved") @set:PropertyName("isApproved") var isApproved: Boolean = false,
    var password: String = "" // This will be the user's phone number by default
)

data class AppNotification(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val message: String = "",
    val targetHouse: String? = null,
    val isForAdmin: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    @get:PropertyName("isRead") @set:PropertyName("isRead") var isRead: Boolean = false,
    val priority: NotificationPriority = NotificationPriority.NORMAL
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
    val timestamp: Long = System.currentTimeMillis(),
    val reactions: Map<String, String> = emptyMap(), // Key: userId, Value: emoji
    val sharedBy: List<String> = emptyList()
)

data class WaterSchedule(
    val id: String = UUID.randomUUID().toString(),
    val valveNumber: String = "",
    val startDateTimeMillis: Long = 0L,
    val endDateTimeMillis: Long = 0L,
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
    
    private val _admins = mutableStateListOf<Admin>()
    val admins: List<Admin> get() = _admins
    
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
                _complaints.sortByDescending { it.id }
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

        // Observe Admins
        database.child("admins").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                _admins.clear()
                snapshot.children.forEach { child ->
                    child.getValue(Admin::class.java)?.let { _admins.add(it) }
                }
            }
            override fun onCancelled(error: DatabaseError) { Log.e("Firebase", error.message) }
        })
    }

    fun addAdmin(name: String, password: String) {
        val admin = Admin(name = name, password = password)
        database.child("admins").child(admin.id).setValue(admin)
        notificationMessage = "Admin '$name' added"
    }

    fun editAdmin(admin: Admin) {
        database.child("admins").child(admin.id).setValue(admin)
        notificationMessage = "Admin '${admin.name}' updated"
    }

    fun deleteAdmin(admin: Admin) {
        database.child("admins").child(admin.id).removeValue()
        notificationMessage = "Admin '${admin.name}' deleted"
    }

    fun registerUser(name: String, phone: String, cnic: String, houseNumber: String, userType: String) {
        val user = UserProfile(name, phone, cnic, houseNumber, userType, password = phone) // Default password is phone number
        database.child("users").child(phone).setValue(user)

        val notification = AppNotification(
            title = "New User Pending Approval",
            message = "$name ($houseNumber) is waiting for registration approval.",
            isForAdmin = true
        )
        database.child("notifications").push().setValue(notification)
    }

    fun approveUser(user: UserProfile) {
        database.child("users").child(user.phone).child("isApproved").setValue(true)
        val notification = AppNotification(
            title = "Registration Approved",
            message = "Welcome, ${user.name}! Your registration has been approved. You can now log in with your house number and phone number as the password.",
            targetHouse = user.houseNumber
        )
        database.child("notifications").push().setValue(notification)
        notificationMessage = "User ${user.name} has been approved."
    }
    
    fun changeUserPassword(user: UserProfile, newPassword: String) {
        if (newPassword.isNotBlank()) {
            database.child("users").child(user.phone).child("password").setValue(newPassword)
            notificationMessage = "Password updated successfully."
        }
    }

    fun sendMaintenanceNotification(houseNumber: String, amount: String, isHighPriority: Boolean) {
        val priority = if (isHighPriority) NotificationPriority.HIGH else NotificationPriority.NORMAL
        addNotificationToFirebase(
            title = "Maintenance Charges Due",
            message = "Dear User, your maintenance charges of Rs. $amount are due.",
            house = houseNumber,
            priority = priority
        )
    }

    private fun addNotificationToFirebase(title: String, message: String, house: String? = null, forAdmin: Boolean = false, priority: NotificationPriority = NotificationPriority.NORMAL) {
        val notification = AppNotification(title = title, message = message, targetHouse = house, isForAdmin = forAdmin, priority = priority)
        database.child("notifications").push().setValue(notification)
        notificationMessage = message
    }

    fun markNotificationAsRead(notificationId: String, isAdmin: Boolean) {
        database.child("notifications").orderByChild("id").equalTo(notificationId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                snapshot.children.firstOrNull()?.let { notificationSnapshot ->
                    val notification = notificationSnapshot.getValue(AppNotification::class.java)
                    if (notification != null) {
                        if (notification.priority == NotificationPriority.HIGH && !isAdmin) {
                            // Non-admins cannot mark high-priority notifications as read
                            notificationMessage = "High-priority notifications can only be dismissed by an admin."
                            return
                        } 
                        notificationSnapshot.ref.child("isRead").setValue(true)
                    }
                }
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
                        val isAdminNotification = notification.isForAdmin
                        val isUserNotification = !notification.isForAdmin && (notification.targetHouse == null || notification.targetHouse == userHouse)
                        
                        if (isAdmin && isAdminNotification) {
                             child.ref.child("isRead").setValue(true)
                        } else if (!isAdmin && isUserNotification) {
                            if (notification.priority != NotificationPriority.HIGH) {
                                child.ref.child("isRead").setValue(true)
                            }
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

    fun addReactionToAnnouncement(announcementId: Int, reaction: String, userId: String, isAdmin: Boolean) {
    if (isAdmin) return
        val announcementRef = database.child("announcements").child(announcementId.toString())
        announcementRef.child("reactions").get().addOnSuccessListener { dataSnapshot ->
            val typeIndicator = object : GenericTypeIndicator<MutableMap<String, String>>() {}
            val reactions = dataSnapshot.getValue(typeIndicator) ?: mutableMapOf()

            // If the user has already reacted with the same emoji, remove their reaction.
            if (reactions[userId] == reaction) {
                reactions.remove(userId)
            } else {
                // Otherwise, add or update their reaction.
                reactions[userId] = reaction
            }

            announcementRef.child("reactions").setValue(reactions)
        }.addOnFailureListener {
            Log.e("Firebase", "Failed to update reaction", it)
        }
    }

    fun shareAnnouncement(announcementId: Int, userId: String) {
        val announcementRef = database.child("announcements").child(announcementId.toString())
        announcementRef.child("sharedBy").get().addOnSuccessListener {
            val sharedByList = it.getValue(object : GenericTypeIndicator<List<String>>() {}) ?: emptyList()
            if (!sharedByList.contains(userId)) {
                val newList = sharedByList.toMutableList()
                newList.add(userId)
                announcementRef.child("sharedBy").setValue(newList)
            }
        }
    }

    fun addWaterSchedule(valveNumber: String, startDateTimeMillis: Long, endDateTimeMillis: Long) {
        val schedule = WaterSchedule(
            id = UUID.randomUUID().toString(),
            valveNumber = valveNumber,
            startDateTimeMillis = startDateTimeMillis,
            endDateTimeMillis = endDateTimeMillis,
            timestamp = System.currentTimeMillis()
        )
        database.child("waterSchedules").child(schedule.id).setValue(schedule)
        val startDate = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(startDateTimeMillis))
        val endDate = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(endDateTimeMillis))
        addNotificationToFirebase("Schedule Update", "Water schedule for $valveNumber from $startDate to $endDate has been added")
    }

    fun editWaterSchedule(schedule: WaterSchedule) {
        database.child("waterSchedules").child(schedule.id).setValue(schedule)
        val date = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(schedule.startDateTimeMillis))
        addNotificationToFirebase("Schedule Update", "Water schedule for ${schedule.valveNumber} on $date has been updated")
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

enum class AuthState {
    LOGIN, REGISTER, PENDING_APPROVAL, LOGGED_IN
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComplaintPortalApp(viewModel: SocietyViewModel = viewModel()) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE) }
    val snackbarHostState = remember { SnackbarHostState() }
    val themeViewModel: ThemeViewModel = viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ThemeViewModel(sharedPrefs) as T
        }
    })

    var authState by remember { mutableStateOf(AuthState.LOGIN) }
    var currentUser by remember { mutableStateOf<UserProfile?>(null) }
    var showExitDialog by remember { mutableStateOf(false) }

    // Check initial auth state
    LaunchedEffect(viewModel.registeredUsers) {
        val loggedInHouse = sharedPrefs.getString("user_house", null)
        if (loggedInHouse != null) {
            val user = viewModel.registeredUsers.find { it.houseNumber.equals(loggedInHouse, ignoreCase = true) }
            if (user != null) {
                currentUser = user
                authState = if (user.isApproved) AuthState.LOGGED_IN else AuthState.PENDING_APPROVAL
            } else {
                authState = AuthState.LOGIN // User might have been deleted
            }
        } else {
            authState = AuthState.LOGIN
        }
    }

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

    var isAdminMode by remember { mutableStateOf(false) }
    var isSuperAdminMode by remember { mutableStateOf(false) }
    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var showNotificationCenter by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var showWelcomeScreen by remember { mutableStateOf(true) }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Confirm Exit") },
            text = { Text("Are you sure you want to exit the app?") },
            confirmButton = {
                Button(onClick = { exitProcess(0) }) {
                    Text("Exit")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    BackHandler(enabled = authState == AuthState.LOGGED_IN && selectedTab == 0) {
        showExitDialog = true
    }

    BackHandler(enabled = authState == AuthState.LOGGED_IN && selectedTab != 0) {
        selectedTab = 0
    }

    ComplaintPortalTheme(themeViewModel = themeViewModel) {
        Box(modifier = Modifier.fillMaxSize()) {
            when (authState) {
                AuthState.LOGIN -> {
                    LoginScreen(
                        onLogin = { houseNumber, password ->
                            val user = viewModel.registeredUsers.find { it.houseNumber.equals(houseNumber, ignoreCase = true) }
                            if (user != null && user.password == password) {
                                if (user.isApproved) {
                                    sharedPrefs.edit().putString("user_house", user.houseNumber).apply()
                                    currentUser = user
                                    authState = AuthState.LOGGED_IN
                                } else {
                                    authState = AuthState.PENDING_APPROVAL
                                }
                            } else {
                                Toast.makeText(context, "Invalid Credentials", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onRegister = { authState = AuthState.REGISTER },
                        onAdminLogin = { adminName, password ->
                             val admin = viewModel.admins.find { it.name.equals(adminName, ignoreCase = true) && it.password == password }
                             if (admin != null || (adminName.equals("admin", ignoreCase = true) && password == viewModel.adminPassword)) {
                                isAdminMode = true
                                authState = AuthState.LOGGED_IN
                            } else {
                                Toast.makeText(context, "Invalid Credentials", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
                AuthState.REGISTER -> {
                    RegistrationScreen(
                        onRegister = { name, phone, cnic, house, type ->
                            viewModel.registerUser(name, phone, cnic, house, type)
                            authState = AuthState.PENDING_APPROVAL
                        },
                        onBackToLogin = { authState = AuthState.LOGIN }
                    )
                }
                AuthState.PENDING_APPROVAL -> {
                    PendingApprovalScreen(onLogout = {
                        sharedPrefs.edit().clear().apply()
                        authState = AuthState.LOGIN
                    })
                }
                AuthState.LOGGED_IN -> {
                     if (showWelcomeScreen && !isAdminMode && !isSuperAdminMode) {
                        WelcomeScreen(onEnter = { showWelcomeScreen = false })
                    } else {
                        val isAdmin = isAdminMode || isSuperAdminMode
                        val registeredHouse = currentUser?.houseNumber ?: ""

                        if (showNotificationCenter) {
                            NotificationCenterDialog(
                                notifications = viewModel.notifications,
                                isAdmin = isAdmin,
                                userHouse = registeredHouse,
                                onMarkRead = { notificationId -> viewModel.markNotificationAsRead(notificationId, isAdmin) },
                                onMarkAllRead = { viewModel.markAllNotificationsAsRead(isAdmin, registeredHouse) },
                                onDismiss = { showNotificationCenter = false }
                            )
                        }
                        
                        if (showChangePasswordDialog) {
                            if (isAdmin) {
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
                            } else {
                                var newPassword by remember { mutableStateOf("") }
                                AlertDialog(
                                    onDismissRequest = { showChangePasswordDialog = false },
                                    title = { Text("Change Your Password") },
                                    text = {
                                        OutlinedTextField(
                                            value = newPassword,
                                            onValueChange = { newPassword = it },
                                            label = { Text("New Password") },
                                            visualTransformation = PasswordVisualTransformation(),
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    },
                                    confirmButton = {
                                        Button(onClick = {
                                            currentUser?.let { user ->
                                                viewModel.changeUserPassword(user, newPassword)
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
                        }

                        Scaffold(
                            modifier = Modifier.imePadding(),
                            topBar = {
                                TopAppBar(
                                    title = { 
                                        val userName = currentUser?.name ?: "Resident"
                                        Text(
                                            if (isSuperAdminMode) "Super Admin" 
                                            else if (isAdminMode) "Admin Panel" 
                                            else "Hi, $userName", 
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSuperAdminMode) MaterialTheme.colorScheme.primary else Color.Unspecified
                                        )
                                    },
                                    actions = {
                                        IconButton(onClick = { showNotificationCenter = true }) {
                                            BadgedBox(
                                                badge = { 
                                                    val unreadCount = if (isAdmin) {
                                                        viewModel.notifications.count { !it.isRead && it.isForAdmin }
                                                    } else {
                                                        viewModel.notifications.count { !it.isRead && (!it.isForAdmin && (it.targetHouse == null || it.targetHouse == registeredHouse)) }
                                                    }
                                                    if (unreadCount > 0) {
                                                        Badge { Text(unreadCount.toString()) }
                                                    }
                                                }
                                            ) {
                                                Icon(Icons.Default.Notifications, contentDescription = "Notifications")
                                            }
                                        }
                                        ThemeSelector(themeViewModel = themeViewModel)
                                        IconButton(onClick = { showChangePasswordDialog = true }) {
                                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                                        }
                                        Button(
                                            onClick = { 
                                                isAdminMode = false
                                                isSuperAdminMode = false
                                                sharedPrefs.edit().clear().apply()
                                                authState = AuthState.LOGIN
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                        ) {
                                            Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Logout", tint = Color.White)
                                            Spacer(Modifier.width(4.dp))
                                            Text("Logout", color = Color.White)
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
                                    if (isAdminMode || isSuperAdminMode) {
                                        NavigationBarItem(
                                            icon = { Icon(Icons.Default.People, contentDescription = null) },
                                            label = { Text("Users") },
                                            selected = selectedTab == 3,
                                            onClick = { selectedTab = 3 }
                                        )
                                    }
                                    if (isSuperAdminMode) {
                                        NavigationBarItem(
                                            icon = { Icon(Icons.Default.AdminPanelSettings, contentDescription = null) },
                                            label = { Text("Admins") },
                                            selected = selectedTab == 4,
                                            onClick = { selectedTab = 4 }
                                        )
                                    }
                                }
                            }
                        ) { innerPadding ->
                            PullToRefreshBox(
                                isRefreshing = viewModel.isRefreshing,
                                onRefresh = { viewModel.refreshData() },
                                modifier = Modifier.padding(innerPadding).fillMaxSize()
                            ) {
                                val userName = currentUser?.name ?: "Resident"
                                Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                                    when (selectedTab) {
                                        0 -> if (isAdmin) AdminComplaintScreen(viewModel, isSuperAdminMode) else UserComplaintScreen(viewModel, userName)
                                        1 -> WaterScheduleScreen(viewModel, isAdmin)
                                        2 -> AnnouncementScreen(viewModel, isAdmin)
                                        3 -> if (isAdmin) RegisteredUsersScreen(viewModel) else Box {}
                                        4 -> if (isSuperAdminMode) AdminManagementScreen(viewModel) else Box { }
                                    }
                                }
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
}

@Composable
fun SendMaintenanceNotificationDialog(
    user: UserProfile,
    onDismiss: () -> Unit,
    onSend: (String, String, Boolean) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var isHighPriority by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Send Maintenance Notification") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("To: ${user.name} (${user.houseNumber})")
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isHighPriority, onCheckedChange = { isHighPriority = it })
                    Text("High Priority (3+ months due)")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSend(user.houseNumber, amount, isHighPriority)
                    onDismiss()
                },
                enabled = amount.isNotBlank()
            ) {
                Text("Send")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun UserDetailsDialog(
    user: UserProfile,
    onDismiss: () -> Unit,
    onSendNotification: () -> Unit,
    onApprove: () -> Unit,
    isAdmin: Boolean
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(user.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Phone: ${user.phone}")
                Text("CNIC: ${user.cnic}")
                Text("House: ${user.houseNumber}")
                Text("Type: ${user.userType}")
                Text("Registered: ${SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(user.registrationDate))}")
                if (isAdmin) {
                    Text("Status: ${if (user.isApproved) "Approved" else "Pending"}", color = if (user.isApproved) Color.Green else Color.Red)
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                if (isAdmin && !user.isApproved) {
                    Button(onClick = onApprove) {
                        Text("Approve")
                    }
                    Spacer(Modifier.width(8.dp))
                }
                Button(onClick = onSendNotification) {
                    Text("Notify")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun RegisteredUsersScreen(viewModel: SocietyViewModel) {
    val context = LocalContext.current
    val users = viewModel.registeredUsers.sortedByDescending { it.registrationDate }
    var selectedUser by remember { mutableStateOf<UserProfile?>(null) }
    var showSendNotificationDialog by remember { mutableStateOf(false) }

    if (selectedUser != null) {
        UserDetailsDialog(
            user = selectedUser!!,
            onDismiss = { selectedUser = null },
            onSendNotification = { 
                showSendNotificationDialog = true
            },
            onApprove = {
                viewModel.approveUser(selectedUser!!)
                selectedUser = null
            },
            isAdmin = true // This screen is admin-only
        )
    }

    if (showSendNotificationDialog) {
        SendMaintenanceNotificationDialog(
            user = selectedUser!!,
            onDismiss = { showSendNotificationDialog = false },
            onSend = { houseNumber, amount, isHighPriority ->
                viewModel.sendMaintenanceNotification(houseNumber, amount, isHighPriority)
                showSendNotificationDialog = false
                selectedUser = null
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Registered Users (${users.size})", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Button(onClick = {
                val csvContent = createUsersCsv(users)
                downloadUsersCsv(context, csvContent)
            }) {
                Icon(Icons.Default.Download, contentDescription = "Download CSV")
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(users) { user ->
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth().clickable { selectedUser = user },
                    border = BorderStroke(1.dp, if (user.isApproved) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.error)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            val icon = if (user.userType.equals("Owner", ignoreCase = true)) Icons.Default.Person else Icons.Default.Group
                            Icon(icon, contentDescription = user.userType, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(32.dp))
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(user.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                                Text("House: ${user.houseNumber}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (!user.isApproved) {
                                    Text("(Pending Approval)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = "Details")
                    }
                }
            }
        }
    }
}

private fun createUsersCsv(users: List<UserProfile>): String {
    val header = "Name,Phone,CNIC,House Number,User Type,Registration Date,Approved"
    val rows = users.map { user ->
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(user.registrationDate))
        "\"${user.name.replace("\"", "\"\"")}\",\"${user.phone}\",\"${user.cnic}\",\"${user.houseNumber.replace("\"", "\"\"")}\",\"${user.userType}\",\"$date\",\"${user.isApproved}\""
    }
    return (listOf(header) + rows).joinToString("\n")
}

private fun downloadUsersCsv(context: Context, content: String) {
    val fileName = "registered_users_${System.currentTimeMillis()}.csv"
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
        put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
    }

    val resolver = context.contentResolver
    try {
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
        uri?.let {
            resolver.openOutputStream(it)?.use { outputStream ->
                outputStream.write(content.toByteArray())
                outputStream.flush()
            }
            Toast.makeText(context, "CSV saved to Downloads folder", Toast.LENGTH_LONG).show()
        } ?: throw Exception("MediaStore URI was null")
    } catch (e: Exception) {
        Log.e("CSV_DOWNLOAD", "Failed to save CSV", e)
        Toast.makeText(context, "Error: Could not save CSV file.", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun AdminManagementScreen(viewModel: SocietyViewModel) {
    var adminName by remember { mutableStateOf("") }
    var adminPassword by remember { mutableStateOf("") }
    var editingAdmin by remember { mutableStateOf<Admin?>(null) }

    if (editingAdmin != null) {
        EditAdminDialog(
            admin = editingAdmin!!,
            onDismiss = { editingAdmin = null },
            onSave = {
                viewModel.editAdmin(it)
                editingAdmin = null
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text("Manage Admins", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = adminName,
            onValueChange = { adminName = it },
            label = { Text("Admin Name") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = adminPassword,
            onValueChange = { adminPassword = it },
            label = { Text("Admin Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                if (adminName.isNotBlank() && adminPassword.isNotBlank()) {
                    viewModel.addAdmin(adminName, adminPassword)
                    adminName = ""
                    adminPassword = ""
                }
            },
            modifier = Modifier.align(Alignment.End)
        ) {
            Text("Add Admin")
        }
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))
        Text("Current Admins", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(viewModel.admins) { admin ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(admin.name)
                    Row {
                        IconButton(onClick = { editingAdmin = admin }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit")
                        }
                        IconButton(onClick = { viewModel.deleteAdmin(admin) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EditAdminDialog(
    admin: Admin,
    onDismiss: () -> Unit,
    onSave: (Admin) -> Unit
) {
    var adminName by remember { mutableStateOf(admin.name) }
    var adminPassword by remember { mutableStateOf("") } // Password should be re-entered for security

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Admin") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = adminName,
                    onValueChange = { adminName = it },
                    label = { Text("Admin Name") }
                )
                OutlinedTextField(
                    value = adminPassword,
                    onValueChange = { adminPassword = it },
                    label = { Text("New Password (optional)") },
                    visualTransformation = PasswordVisualTransformation()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val updatedAdmin = admin.copy(
                    name = adminName,
                    password = if (adminPassword.isNotBlank()) adminPassword else admin.password
                )
                onSave(updatedAdmin)
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ThemeSelector(themeViewModel: ThemeViewModel) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.Palette, contentDescription = "Theme")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            AppTheme.entries.forEach { theme ->
                DropdownMenuItem(
                    text = { Text(theme.displayName) },
                    onClick = { 
                        themeViewModel.setTheme(theme)
                        expanded = false 
                    }
                )
            }
        }
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
                notifications.filter { it.isForAdmin }
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
                                .background(
                                    when {
                                        !notification.isRead && notification.priority == NotificationPriority.HIGH ->
                                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                                        !notification.isRead ->
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                                        else -> Color.Transparent
                                    }
                                )
                                .clickable { onMarkRead(notification.id) }
                                .padding(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val icon = when {
                                    notification.priority == NotificationPriority.HIGH -> Icons.Default.Error
                                    notification.isForAdmin -> Icons.Default.Warning
                                    else -> Icons.Default.Info
                                }
                                val tint = when {
                                    notification.priority == NotificationPriority.HIGH -> MaterialTheme.colorScheme.error
                                    notification.isForAdmin -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.primary
                                }
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = tint,
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

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun RegistrationScreen(
    onRegister: (String, String, String, String, String) -> Unit,
    onBackToLogin: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var cnic by remember { mutableStateOf("") }
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
                    value = cnic,
                    onValueChange = { input ->
                        if (input.length <= 13 && input.all { it.isDigit() }) {
                            cnic = input
                        }
                    },
                    label = { Text("CNIC (13 digits, no dashes)") },
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
                        if (name.isNotBlank() && phone.length == 11 && cnic.length == 13 && houseNumber.isNotBlank()) {
                            onRegister(name, phone, cnic, houseNumber, userType)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = name.isNotBlank() && phone.length == 11 && cnic.length == 13 && houseNumber.isNotBlank()
                ) {
                    Text("Register", fontSize = 18.sp)
                }

                TextButton(onClick = onBackToLogin) {
                    Text("Back to Login")
                }
            }
        }
    }
}


@Composable
fun LoginScreen(
    onLogin: (String, String) -> Unit,
    onRegister: () -> Unit,
    onAdminLogin: (String, String) -> Unit
) {
    var houseNumber by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showAdminLogin by remember { mutableStateOf(false) }

    var adminName by remember { mutableStateOf("") }
    var adminPassword by remember { mutableStateOf("") }

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
            AnimatedContent(
                targetState = showAdminLogin,
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally { height -> height } + fadeIn()).togetherWith(slideOutHorizontally { height -> -height } + fadeOut())
                    } else {
                        (slideInHorizontally { height -> -height } + fadeIn()).togetherWith(slideOutHorizontally { height -> height } + fadeOut())
                    }.using(
                        SizeTransform(clip = false)
                    )
                }, label = ""
            ) { targetState ->
                if (!targetState) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Resident Login",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )

                        OutlinedTextField(
                            value = houseNumber,
                            onValueChange = { houseNumber = it },
                            label = { Text("House Number") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Password (your phone number)") },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Button(
                            onClick = { onLogin(houseNumber, password) },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text("Login", fontSize = 18.sp)
                        }
                        
                        TextButton(onClick = onRegister) {
                            Text("New User? Register here")
                        }

                        TextButton(onClick = { showAdminLogin = true }) {
                            Text("Login as Admin")
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            Icons.Default.AdminPanelSettings,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Admin Login",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )

                        OutlinedTextField(
                            value = adminName,
                            onValueChange = { adminName = it },
                            label = { Text("Admin Name") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = adminPassword,
                            onValueChange = { adminPassword = it },
                            label = { Text("Password") },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Button(
                            onClick = { onAdminLogin(adminName, adminPassword) },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text("Login", fontSize = 18.sp)
                        }

                        TextButton(onClick = { showAdminLogin = false }) {
                            Text("Resident Login")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PendingApprovalScreen(onLogout: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            Icon(
                Icons.Default.HourglassEmpty,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(24.dp))
            Text(
                "Pending Approval",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Your registration is being reviewed by the admin. You will be notified once it's approved.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )
             Spacer(Modifier.height(32.dp))
            Button(onClick = onLogout, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                Text("Logout")
            }
        }
    }
}

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Info, contentDescription = "About") },
        title = { Text(text = "About") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "Version 1.1.0", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                Text(text = "Developed by: Zahid Choudhry", style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun WelcomeScreen(onEnter: () -> Unit) {
    var showAboutDialog by remember { mutableStateOf(false) }

    if (showAboutDialog) {
        AboutDialog(onDismiss = { showAboutDialog = false })
    }
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
                "Your Smart Residents Portal",
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

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .padding(bottom = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { showAboutDialog = true }
                    .padding(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SupervisorAccount, // Placeholder for the logo
                    contentDescription = "About",
                    modifier = Modifier.size(60.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Progressive Panel",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaterScheduleScreen(viewModel: SocietyViewModel, isAdmin: Boolean) {
    var showEditDialog by remember { mutableStateOf(false) }
    var editingSchedule by remember { mutableStateOf<WaterSchedule?>(null) }
    var viewingSchedule by remember { mutableStateOf<WaterSchedule?>(null) }
    var showCalendar by remember { mutableStateOf(false) }

    if (showEditDialog) {
        EditWaterScheduleDialog(
            schedule = editingSchedule,
            onDismiss = { showEditDialog = false },
            onSave = {
                if (editingSchedule == null) {
                    viewModel.addWaterSchedule(it.valveNumber, it.startDateTimeMillis, it.endDateTimeMillis)
                } else {
                    viewModel.editWaterSchedule(it)
                }
                showEditDialog = false
            }
        )
    }

    if (viewingSchedule != null) {
        WaterScheduleDetailDialog(schedule = viewingSchedule!!, onDismiss = { viewingSchedule = null })
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Water Supply Schedule", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Row {
                if (isAdmin) {
                    Button(onClick = { 
                        editingSchedule = null
                        showEditDialog = true 
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Schedule")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add")
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = { showCalendar = !showCalendar }) {
                    Icon(Icons.Default.DateRange, contentDescription = "Toggle Calendar")
                }
            }
        }
        
        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        if (showCalendar) {
            Calendar(viewModel.waterSchedules)
        } else {
            val now = System.currentTimeMillis()
            val allSchedules = viewModel.waterSchedules
                .filter { it.endDateTimeMillis >= now }

            val (activeSchedules, upcomingSchedules) = allSchedules.partition { 
                it.startDateTimeMillis <= now 
            }

            val schedulesToShow = activeSchedules + upcomingSchedules

            if (schedulesToShow.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("No upcoming water schedules.", color = Color.Gray)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(schedulesToShow) { schedule ->
                        val isActive = now >= schedule.startDateTimeMillis && now <= schedule.endDateTimeMillis
                        WaterScheduleCard(
                            schedule = schedule,
                            isAdmin = isAdmin,
                            isActive = isActive,
                            onDelete = { viewModel.deleteWaterSchedule(schedule.id) },
                            onClick = {
                                if (isAdmin) {
                                    editingSchedule = it
                                    showEditDialog = true
                                } else {
                                    viewingSchedule = it
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun Calendar(schedules: List<WaterSchedule>) {
    val calendar = Calendar.getInstance()
    val today = calendar.get(Calendar.DAY_OF_YEAR)
    val currentMonth = calendar.get(Calendar.MONTH)
    val currentYear = calendar.get(Calendar.YEAR)

    var selectedMonth by remember { mutableStateOf(currentMonth) }
    var selectedYear by remember { mutableStateOf(currentYear) }
    var selectedSchedules by remember { mutableStateOf<List<WaterSchedule>>(emptyList()) }

    if (selectedSchedules.isNotEmpty()) {
        DayScheduleDialog(
            schedules = selectedSchedules,
            onDismiss = { selectedSchedules = emptyList() }
        )
    }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { 
                if (selectedMonth == 0) {
                    selectedMonth = 11
                    selectedYear--
                } else {
                    selectedMonth--
                }
            }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous Month")
            }
            Text(
                text = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(calendar.apply { set(selectedYear, selectedMonth, 1) }.time),
                style = MaterialTheme.typography.titleMedium
            )
            IconButton(onClick = { 
                if (selectedMonth == 11) {
                    selectedMonth = 0
                    selectedYear++
                } else {
                    selectedMonth++
                }
            }) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Month")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        LazyVerticalGrid(columns = GridCells.Fixed(7)) {
            // Days of the week
            items(listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")) { day ->
                Text(text = day, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
            }

            calendar.set(selectedYear, selectedMonth, 1)
            val firstDayOfMonth = calendar.get(Calendar.DAY_OF_WEEK) - 1
            val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)

            items(firstDayOfMonth) { 
                Box(modifier = Modifier.size(40.dp))
            }

            items(daysInMonth) { day ->
                val date = calendar.apply { set(selectedYear, selectedMonth, day + 1) }.timeInMillis
                val schedulesForDay = schedules.filter {
                    val scheduleCalendar = Calendar.getInstance().apply { timeInMillis = it.startDateTimeMillis }
                    scheduleCalendar.get(Calendar.YEAR) == selectedYear &&
                    scheduleCalendar.get(Calendar.MONTH) == selectedMonth &&
                    scheduleCalendar.get(Calendar.DAY_OF_MONTH) == day + 1
                }

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (schedulesForDay.isNotEmpty()) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                        .clickable { selectedSchedules = schedulesForDay },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = (day + 1).toString())
                }
            }
        }
    }
}

@Composable
fun DayScheduleDialog(schedules: List<WaterSchedule>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Schedules for the Day") },
        text = {
            LazyColumn {
                if (schedules.isEmpty()) {
                    item { Text("No schedules for this day.") }
                } else {
                    items(schedules) { schedule ->
                        WaterScheduleCard(
                            schedule = schedule,
                            isAdmin = false,
                            isActive = false,
                            onDelete = {},
                            onClick = {}
                        )
                        Spacer(modifier = Modifier.height(8.dp))
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
fun WaterScheduleDetailDialog(schedule: WaterSchedule, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.WaterDrop, contentDescription = null) },
        title = { Text("Water Schedule Details") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Valve / Area: ${schedule.valveNumber}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                
                HorizontalDivider()

                val format = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                
                Row {
                    Text("Starts: ", fontWeight = FontWeight.Bold)
                    Text(format.format(Date(schedule.startDateTimeMillis)))
                }
                 Row {
                    Text("Ends:   ", fontWeight = FontWeight.Bold)
                    Text(format.format(Date(schedule.endDateTimeMillis)))
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun EditWaterScheduleDialog(
    schedule: WaterSchedule?,
    onDismiss: () -> Unit,
    onSave: (WaterSchedule) -> Unit
) {
    val context = LocalContext.current
    var valveNumber by remember { mutableStateOf(schedule?.valveNumber ?: "") }
    val initialStartDate = schedule?.startDateTimeMillis ?: System.currentTimeMillis()
    val initialEndDate = schedule?.endDateTimeMillis ?: (System.currentTimeMillis() + 3600000) // 1 hour later

    var startDateTime by remember { mutableStateOf(Calendar.getInstance().apply { timeInMillis = initialStartDate }) }
    var endDateTime by remember { mutableStateOf(Calendar.getInstance().apply { timeInMillis = initialEndDate }) }

    val dateTimeFormat = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }

    val startDatePickerDialog = DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            startDateTime.set(year, month, dayOfMonth)
        },
        startDateTime.get(Calendar.YEAR),
        startDateTime.get(Calendar.MONTH),
        startDateTime.get(Calendar.DAY_OF_MONTH)
    )

    val startTimePickerDialog = TimePickerDialog(
        context,
        { _, hour, minute -> 
            startDateTime.set(Calendar.HOUR_OF_DAY, hour)
            startDateTime.set(Calendar.MINUTE, minute)
        },
        startDateTime.get(Calendar.HOUR_OF_DAY),
        startDateTime.get(Calendar.MINUTE),
        false
    )

    val endDatePickerDialog = DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            endDateTime.set(year, month, dayOfMonth)
        },
        endDateTime.get(Calendar.YEAR),
        endDateTime.get(Calendar.MONTH),
        endDateTime.get(Calendar.DAY_OF_MONTH)
    )

    val endTimePickerDialog = TimePickerDialog(
        context,
        { _, hour, minute ->
            endDateTime.set(Calendar.HOUR_OF_DAY, hour)
            endDateTime.set(Calendar.MINUTE, minute)
        },
        endDateTime.get(Calendar.HOUR_OF_DAY),
        endDateTime.get(Calendar.MINUTE),
        false
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (schedule == null) "Add Schedule" else "Edit Schedule") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = valveNumber,
                    onValueChange = { valveNumber = it },
                    label = { Text("Valve Number / Area") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text("FROM", style = MaterialTheme.typography.labelSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                     Button(onClick = { startDatePickerDialog.show() }, modifier = Modifier.weight(1f)) { Text("Date") }
                     Button(onClick = { startTimePickerDialog.show() }, modifier = Modifier.weight(1f)) { Text("Time") }
                }
                Text(dateTimeFormat.format(startDateTime.time), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())

                HorizontalDivider(modifier = Modifier.padding(vertical=8.dp))

                Text("TO", style = MaterialTheme.typography.labelSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                     Button(onClick = { endDatePickerDialog.show() }, modifier = Modifier.weight(1f)) { Text("Date") }
                     Button(onClick = { endTimePickerDialog.show() }, modifier = Modifier.weight(1f)) { Text("Time") }
                }
                Text(dateTimeFormat.format(endDateTime.time), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = {
                val newSchedule = schedule?.copy(
                    valveNumber = valveNumber,
                    startDateTimeMillis = startDateTime.timeInMillis,
                    endDateTimeMillis = endDateTime.timeInMillis
                ) ?: WaterSchedule(
                    valveNumber = valveNumber,
                    startDateTimeMillis = startDateTime.timeInMillis,
                    endDateTimeMillis = endDateTime.timeInMillis
                )
                onSave(newSchedule)
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}


@Composable
fun WaterScheduleCard(
    schedule: WaterSchedule, 
    isAdmin: Boolean, 
    isActive: Boolean,
    onDelete: () -> Unit, 
    onClick: (WaterSchedule) -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().clickable { onClick(schedule) },
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
                    if (isActive) {
                        Spacer(Modifier.width(8.dp))
                        StatusBadge(statusText = "VALVE OPEN", color = Color(0xFF81C784))
                    }
                }
                Spacer(Modifier.height(8.dp))
                val format = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                Text(
                    text = "From: ${format.format(Date(schedule.startDateTimeMillis))}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = "To:      ${format.format(Date(schedule.endDateTimeMillis))}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            if (isAdmin) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserComplaintScreen(viewModel: SocietyViewModel, userName: String) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE) }
    val registeredHouse = sharedPrefs.getString("user_house", "") ?: ""
    val registeredPhone = sharedPrefs.getString("user_phone", "") ?: ""

    val maintenanceNotification = viewModel.notifications
        .filter { it.targetHouse == registeredHouse && it.title == "Maintenance Charges Due" && !it.isRead }
        .maxByOrNull { it.timestamp }

    var description by remember { mutableStateOf("") }
    var trackCompNumber by remember { mutableStateOf("") }
    var trackedComplaints by remember { mutableStateOf(emptyList<Complaint>()) }
    var lastSubmittedNumber by remember { mutableStateOf<String?>(null) }
    var showComplaints by remember { mutableStateOf(false) }
    var hasSearched by remember { mutableStateOf(false) }
    var selectedComplaint by remember { mutableStateOf<Complaint?>(null) }
    
    val clipboardManager = LocalClipboardManager.current

    if (selectedComplaint != null) {
        Dialog(onDismissRequest = { selectedComplaint = null }) {
            Column {
                ComplaintCard(
                    complaint = selectedComplaint!!,
                    onRatingSubmitted = { rating ->
                        viewModel.submitRating(selectedComplaint!!.id, rating)
                        selectedComplaint = null // Close on submit
                    }
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { selectedComplaint = null },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Close")
                }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        maintenanceNotification?.let { notification ->
            val isHighPriority = notification.priority == NotificationPriority.HIGH
            val amount = notification.message.substringAfter("Rs. ").substringBefore(" ")

            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isHighPriority) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
                ),
                onClick = { /* Maybe mark as read or something */ }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Overdue Maintenance: Rs. $amount",
                        fontWeight = FontWeight.Bold,
                        color = if (isHighPriority) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    if (isHighPriority) {
                        Icon(Icons.Default.Warning, contentDescription = "High Priority", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        Text("Hi $userName", style = MaterialTheme.typography.titleLarge)
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
                        showComplaints = !showComplaints
                        if (showComplaints) {
                            trackedComplaints = viewModel.getComplaints(trackCompNumber, registeredHouse)
                            hasSearched = true
                        } else {
                            hasSearched = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { 
                    Icon(Icons.Default.Search, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (showComplaints) "Hide My Complaints" else "View My Complaints") 
                }

                if (showComplaints) {
                    if (trackedComplaints.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Column(modifier = Modifier.heightIn(max=400.dp).verticalScroll(rememberScrollState())) {
                            trackedComplaints.forEach { complaint ->
                                CompactComplaintCard(complaint = complaint) {
                                    selectedComplaint = complaint
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    } else if (hasSearched) {
                        Text("No complaints found for your house.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminComplaintScreen(viewModel: SocietyViewModel, isSuperAdmin: Boolean) {
    var selectedComplaint by remember { mutableStateOf<Complaint?>(null) }

    if (selectedComplaint != null) {
        Dialog(onDismissRequest = { selectedComplaint = null }) {
            var complaintInDialog by remember { mutableStateOf(selectedComplaint!!) }

            Column {
                AdminComplaintCard(
                    complaint = complaintInDialog,
                    isSuperAdmin = isSuperAdmin,
                    onDelete = {
                        viewModel.deleteComplaint(complaintInDialog.id)
                        selectedComplaint = null
                    },
                    onStatusChange = { newStatus ->
                        viewModel.updateStatus(complaintInDialog.id, newStatus)
                        complaintInDialog = complaintInDialog.copy(status = newStatus)
                    }
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { selectedComplaint = null },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Close")
                }
            }
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Active Complaints", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        items(viewModel.complaints, key = { it.id }) { complaint ->
            val dismissState = rememberSwipeToDismissBoxState(
                confirmValueChange = {
                    if (it == SwipeToDismissBoxValue.EndToStart) {
                        viewModel.deleteComplaint(complaint.id)
                        true
                    } else {
                        false
                    }
                }
            )

            SwipeToDismissBox(
                state = dismissState,
                backgroundContent = {
                    val color = when (dismissState.dismissDirection) {
                        SwipeToDismissBoxValue.EndToStart -> Color.Red.copy(alpha = 0.5f)
                        else -> Color.Transparent
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(color)
                            .padding(12.dp),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = Color.White
                        )
                    }
                }
            ) {
                CompactComplaintCard(complaint = complaint) {
                    selectedComplaint = complaint
                }
            }
        }
    }
}

@Composable
fun AnnouncementScreen(viewModel: SocietyViewModel, isAdmin: Boolean) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedAnnouncement by remember { mutableStateOf<Announcement?>(null) }
    val context = LocalContext.current
    val sharedPrefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
    val userId = sharedPrefs.getString("user_phone", "") ?: ""

    if (selectedAnnouncement != null) {
        AnnouncementDetailsDialog(
            announcement = selectedAnnouncement!!,
            onDismiss = { selectedAnnouncement = null },
            onReact = { reaction ->
                viewModel.addReactionToAnnouncement(selectedAnnouncement!!.id, reaction, userId, isAdmin)
            },
            onShare = { announcement ->
                shareAnnouncementAsText(context, announcement)
            }
        )
    }

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
                    Icon(Icons.Default.Eco, contentDescription = null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Society Announcements", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            }
            items(viewModel.announcements) { announcement ->
                AnnouncementCard(
                    announcement = announcement,
                    isAdmin = isAdmin,
                    userId = userId,
                    registeredUsers = viewModel.registeredUsers,
                    onDelete = { viewModel.deleteAnnouncement(announcement.id) },
                    onReact = { reaction ->
                        viewModel.addReactionToAnnouncement(announcement.id, reaction, userId, isAdmin)
                    },
                    onShare = { 
                        shareAnnouncementAsText(context, announcement)
                        viewModel.shareAnnouncement(announcement.id, userId)
                    },
                    onClick = { selectedAnnouncement = announcement }
                )
            }
        }
        if (isAdmin) {
            FloatingActionButton(onClick = { showAddDialog = true }, modifier = Modifier.align(Alignment.BottomEnd)) {
                Icon(Icons.Default.Add, contentDescription = "Add Announcement")
            }
        }
    }
}

private fun shareAnnouncementAsText(context: Context, announcement: Announcement) {
    val shareText = "**${announcement.title}**\n\n${announcement.content}"
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, shareText)
    }
    context.startActivity(Intent.createChooser(intent, "Share Announcement"))
}

@Composable
fun ReactionDetailsDialog(
    emoji: String,
    userIds: List<String>,
    allUsers: List<UserProfile>,
    onDismiss: () -> Unit
) {
    val reactedUsers = userIds.mapNotNull { userId ->
        allUsers.find { it.phone == userId }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reacted with $emoji") },
        text = {
            if (reactedUsers.isEmpty()) {
                Text("No one has reacted with this emoji yet.")
            } else {
                LazyColumn {
                    items(reactedUsers) { user ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text("${user.name} (${user.houseNumber})", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun AnnouncementDetailsDialog(
    announcement: Announcement, 
    onDismiss: () -> Unit,
    onReact: (String) -> Unit,
    onShare: (Announcement) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(announcement.title) },
        text = { 
            Column {
                Text(announcement.content) 
                Spacer(modifier = Modifier.height(16.dp))
                AnnouncementCardReactions(announcement = announcement, onReact = onReact, onShare = { onShare(announcement) })
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun CompactAnnouncementCard(announcement: Announcement, onClick: () -> Unit) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp)) {
                Text(
                    text = announcement.title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(announcement.timestamp)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = "View Details")
        }
    }
}


@Composable
fun AnnouncementCard(
    announcement: Announcement,
    isAdmin: Boolean,
    userId: String,
    registeredUsers: List<UserProfile>,
    onDelete: () -> Unit,
    onReact: (String) -> Unit,
    onShare: () -> Unit,
    onClick: () -> Unit
) {
    val date = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(announcement.timestamp))
    var reactorsToShow by remember { mutableStateOf<Pair<String, List<String>>?>(null) }

    if (reactorsToShow != null) {
        ReactionDetailsDialog(
            emoji = reactorsToShow!!.first,
            userIds = reactorsToShow!!.second,
            allUsers = registeredUsers,
            onDismiss = { reactorsToShow = null }
        )
    }

    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Default.Eco, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(announcement.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(date, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(announcement.content, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (isAdmin) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                    }
                }
            }

            AnnouncementCardReactions(announcement = announcement, onReact = onReact, onShare = onShare)
        }
    }
}

@Composable
fun AnnouncementCardReactions(
    announcement: Announcement, 
    onReact: (String) -> Unit, 
    onShare: () -> Unit
) {
    val context = LocalContext.current
    val sharedPrefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
    val userId = sharedPrefs.getString("user_phone", "") ?: ""
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp, top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val reactionTypes = listOf("👍", "❤️", "😂")
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            reactionTypes.forEach { emoji ->
                val reactors = announcement.reactions.filterValues { it == emoji }.keys
                val count = reactors.size
                val isSelected = announcement.reactions[userId] == emoji

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = emoji,
                        fontSize = 24.sp,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { onReact(emoji) }
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                else Color.Transparent
                            )
                            .padding(4.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = count.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { 
                                // No-op, since we don't want to show the list of reactors here
                            }
                            .padding(horizontal = 4.dp)
                    )
                }
            }
        }

        IconButton(onClick = onShare) {
            Icon(Icons.Default.Share, contentDescription = "Share")
        }
    }
}

@Composable
fun CompactComplaintCard(complaint: Complaint, onClick: () -> Unit) {
    val statusColor = when (complaint.status) {
        ComplaintStatus.PENDING -> Color(0xFFE57373)
        ComplaintStatus.IN_PROGRESS -> Color(0xFF64B5F6)
        ComplaintStatus.RESOLVED -> Color(0xFF81C784)
    }
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(5.dp)
                    .background(statusColor)
            )
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp)) {
                    Text(
                        text = complaint.complaintNumber,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "House: ${complaint.houseNumber}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusBadge(complaint.status)
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
    StatusBadge(statusText = status.name, color = color)
}

@Composable
fun StatusBadge(statusText: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Text(
            text = statusText,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.5.sp
        )
    }
}
