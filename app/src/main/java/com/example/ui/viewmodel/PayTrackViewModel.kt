package com.example.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.*
import com.example.data.repository.AppRepository
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

class PayTrackViewModel(private val repository: AppRepository) : ViewModel() {

    val sessionUserId: String? get() = repository.sessionManager.userId
    val sessionUserName: String? get() = repository.sessionManager.userName

    // Bottom Navigation State
    private val _currentTab = MutableStateFlow("home")
    val currentTab: StateFlow<String> = _currentTab.asStateFlow()

    // Active Pod Selection (for details detail view, null if none selected)
    private val _selectedPodId = MutableStateFlow<Int?>(null)
    val selectedPodId: StateFlow<Int?> = _selectedPodId.asStateFlow()

    // Auth & Onboarding state
    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _isOnboardingCompleted = MutableStateFlow(false)
    val isOnboardingCompleted: StateFlow<Boolean> = _isOnboardingCompleted.asStateFlow()

    // Notification Feed / In-app push Simulation Alerts
    private val _notifications = MutableStateFlow<List<String>>(emptyList())
    val notifications: StateFlow<List<String>> = _notifications.asStateFlow()

    private val _realNotifications = MutableStateFlow<List<com.example.data.api.NotificationData>>(emptyList())
    val realNotifications: StateFlow<List<com.example.data.api.NotificationData>> = _realNotifications.asStateFlow()

    private val _selectedPodBalances = MutableStateFlow<List<com.example.data.api.PodBalance>>(emptyList())
    val selectedPodBalances: StateFlow<List<com.example.data.api.PodBalance>> = _selectedPodBalances.asStateFlow()

    private val _selectedPodActivity = MutableStateFlow<List<com.example.data.api.ActivityLogData>>(emptyList())
    val selectedPodActivity: StateFlow<List<com.example.data.api.ActivityLogData>> = _selectedPodActivity.asStateFlow()

    // Expose flows from Repository
    val primaryUser: StateFlow<UserProfile?> = repository.primaryUser
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val allUsers: StateFlow<List<UserProfile>> = repository.allUsers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPods: StateFlow<List<Pod>> = repository.allPods
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTransactions: StateFlow<List<Transaction>> = repository.allTransactions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val personalTransactions: StateFlow<List<Transaction>> = repository.personalTransactions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allBills: StateFlow<List<Bill>> = repository.allBills
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val upcomingBills: StateFlow<List<Bill>> = repository.upcomingBills
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allBudgets: StateFlow<List<Budget>> = repository.allBudgets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            val token = repository.sessionManager.token
            if (token != null) {
                _isLoggedIn.value = true
                _isOnboardingCompleted.value = true
                repository.syncAll()
                fetchServerNotifications()
                addNotification("Welcome back! Synced with PayTrack Cloud.")
            } else {
                repository.seedDatabase()
                val user = repository.primaryUser.firstOrNull()
                if (user != null) {
                    _isLoggedIn.value = true
                    _isOnboardingCompleted.value = true
                }
            }
        }
    }

    fun triggerSync() {
        viewModelScope.launch {
            try {
                addNotification("Syncing data with PayTrack Cloud Server...")
                val success = repository.syncAll()
                if (success) {
                    addNotification("Sync complete: Cloud database refreshed cleanly.")
                } else {
                    addNotification("Sync completed with offline mode.")
                }
            } catch (e: Exception) {
                Log.e("PayTrackVM", "triggerSync failed", e)
                addNotification("Sync completed with offline fallback.")
            }
        }
    }

    fun setTab(tabName: String) {
        _currentTab.value = tabName
        if (tabName != "pods_detail") {
            _selectedPodId.value = null
        }
    }

    fun selectPod(podId: Int) {
        _selectedPodId.value = podId
        _currentTab.value = "pods_detail"
        _selectedPodBalances.value = emptyList()
        _selectedPodActivity.value = emptyList()
        fetchPodCloudDetails(podId)
    }

    fun fetchPodCloudDetails(podId: Int) {
        viewModelScope.launch {
            try {
                val currentPods = repository.allPods.firstOrNull() ?: emptyList()
                val targetPod = currentPods.find { it.id == podId }
                if (targetPod != null) {
                    val remoteId = repository.getRemotePodId(targetPod)
                    if (remoteId != null) {
                        val balRes = repository.apiGetPodBalances(targetPod)
                        if (balRes.success && balRes.balances != null) {
                            _selectedPodBalances.value = balRes.balances
                        }
                        val actRes = repository.apiGetPodActivity(targetPod)
                        if (actRes.success && actRes.data != null) {
                            _selectedPodActivity.value = actRes.data
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("PayTrackVM", "Failed to fetch cloud details for pod $podId", e)
            }
        }
    }

    fun settlePodPairwiseDebt(podId: Int, toUserId: String, amount: Double, paymentMethod: String) {
        viewModelScope.launch {
            try {
                addNotification("Submitting settlement of $amount...")
                val currentPods = repository.allPods.firstOrNull() ?: emptyList()
                val targetPod = currentPods.find { it.id == podId }
                if (targetPod != null) {
                    val response = repository.apiSettlePodDebt(
                        targetPod,
                        com.example.data.api.PodSettleRequest(
                            toUserId = toUserId,
                            amount = amount,
                            paymentMethod = paymentMethod
                        )
                    )
                    if (response.success) {
                        addNotification("🤝 Success: Settlement of $amount registered on cloud!")
                        fetchPodCloudDetails(podId)
                        repository.syncAll()
                    } else {
                        addNotification("Settle Error: ${response.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e("PayTrackVM", "Failed to settle pairwise debt", e)
                addNotification("Settlement failed: ${e.localizedMessage}")
            }
        }
    }

    fun addNotification(message: String) {
        Log.d("PayTrackAlert", message)
        _notifications.value = listOf(message) + _notifications.value
    }

    fun clearNotifications() {
        _notifications.value = emptyList()
    }

    // AUTH ACTIONS
    fun simulateLogin(email: String, name: String) {
        viewModelScope.launch {
            try {
                addNotification("Connecting to PayTrack Cloud Server...")
                val regResponse = repository.apiRegister(
                    com.example.data.api.RegisterRequest(
                        name = name,
                        email = email,
                        password = "securepassword123",
                        currency = "USD",
                        primaryUseCase = "couple"
                    )
                )
                if (regResponse.success && regResponse.token != null) {
                    repository.sessionManager.token = regResponse.token
                    repository.sessionManager.userId = regResponse._id
                    repository.sessionManager.userName = regResponse.name
                    repository.sessionManager.userEmail = regResponse.email
                    repository.sessionManager.currency = regResponse.currency
                    repository.sessionManager.isOnboarded = true

                    _isLoggedIn.value = true
                    _isOnboardingCompleted.value = true
                    repository.syncAll()
                    addNotification("Cloud Space Registered! Joined as ${regResponse.name}.")
                } else if (!regResponse.success && regResponse.message?.contains("Duplicate", ignoreCase = true) == true) {
                    val logResponse = repository.apiLogin(email, "securepassword123")
                    if (logResponse.success && logResponse.token != null) {
                        repository.sessionManager.token = logResponse.token
                        repository.sessionManager.userId = logResponse._id
                        repository.sessionManager.userName = logResponse.name
                        repository.sessionManager.userEmail = logResponse.email
                        repository.sessionManager.currency = logResponse.currency
                        repository.sessionManager.isOnboarded = true

                        _isLoggedIn.value = true
                        _isOnboardingCompleted.value = true
                        repository.syncAll()
                        addNotification("Welcome back! Cloud Session authenticated as ${logResponse.name}.")
                    } else {
                        addNotification("Auth Error: ${logResponse.message ?: "Authentication failed."}")
                    }
                } else {
                    addNotification("Failed to join Cloud: ${regResponse.message ?: "Error"}")
                }
            } catch (e: Exception) {
                Log.e("PayTrackAuth", "Auth error", e)
                addNotification("No Connection: ${e.localizedMessage}. Using offline cache.")
                repository.insertUser(
                    UserProfile(
                        name = name,
                        email = email,
                        avatarName = "Richie",
                        currencyCode = "USD",
                        currencySymbol = "$",
                        monthlyIncome = 5000.0,
                        isPrimary = true
                    )
                )
                _isLoggedIn.value = true
                _isOnboardingCompleted.value = true
                repository.seedDatabase()
            }
        }
    }

    fun simulateLogout() {
        viewModelScope.launch {
            repository.clearAllData()
            repository.sessionManager.logout()
            _isLoggedIn.value = false
            _isOnboardingCompleted.value = false
            setTab("home")
            addNotification("Logged out from PayTrack Cloud successfully.")
        }
    }

    fun deleteAccount() {
        viewModelScope.launch {
            try {
                addNotification("Deleting Cloud Account and data...")
                repository.apiDeleteAccount()
                _isLoggedIn.value = false
                _isOnboardingCompleted.value = false
                setTab("home")
                addNotification("Account and associated financials deleted from Cloud or Storage.")
            } catch (e: Exception) {
                Log.e("PayTrackAuth", "Account delete failed", e)
                repository.clearAllData()
                repository.sessionManager.logout()
                _isLoggedIn.value = false
                _isOnboardingCompleted.value = false
                setTab("home")
                addNotification("Local storage cleared. Account deletion will finish next online session.")
            }
        }
    }

    fun loginUser(email: String, passwordRaw: String) {
        viewModelScope.launch {
            try {
                addNotification("Connecting to PayTrack Cloud Server...")
                val logResponse = repository.apiLogin(email, passwordRaw)
                if (logResponse.success && logResponse.token != null) {
                    repository.sessionManager.token = logResponse.token
                    repository.sessionManager.userId = logResponse._id
                    repository.sessionManager.userName = logResponse.name ?: "User"
                    repository.sessionManager.userEmail = logResponse.email ?: email
                    repository.sessionManager.currency = logResponse.currency ?: "USD"
                    repository.sessionManager.isOnboarded = true

                    _isLoggedIn.value = true
                    _isOnboardingCompleted.value = true
                    repository.syncAll()
                    addNotification("Authenticated! Welcome back, ${logResponse.name ?: "User"}.")
                } else {
                    addNotification("Login Error: ${logResponse.message ?: "Invalid email or password."}")
                }
            } catch (e: Exception) {
                Log.e("PayTrackLogin", "Login error", e)
                addNotification("No Connection: ${e.localizedMessage}. Local cache fallback enabled.")
                
                // Offline fallback if local user already exists
                val user = repository.primaryUser.firstOrNull()
                if (user != null && user.email.equals(email, ignoreCase = true)) {
                    _isLoggedIn.value = true
                    _isOnboardingCompleted.value = true
                    addNotification("Offline Access verified for ${user.name}.")
                } else {
                    addNotification("No local cache found for this email.")
                }
            }
        }
    }

    fun completeOnboarding(name: String, email: String, passwordRaw: String, currency: String, income: Double) {
        viewModelScope.launch {
            try {
                addNotification("Configuring cloud space profile...")
                val regResponse = repository.apiRegister(
                    com.example.data.api.RegisterRequest(
                        name = name,
                        email = email,
                        password = passwordRaw,
                        currency = currency,
                        primaryUseCase = "couple"
                    )
                )
                if (regResponse.success && regResponse.token != null) {
                    repository.sessionManager.token = regResponse.token
                    repository.sessionManager.userId = regResponse._id
                    repository.sessionManager.userName = regResponse.name
                    repository.sessionManager.userEmail = regResponse.email
                    repository.sessionManager.currency = regResponse.currency
                    repository.sessionManager.isOnboarded = true

                    repository.apiUpdateProfile(
                        com.example.data.api.ProfileUpdateRequest(
                            name = name,
                            currency = currency,
                            monthlyIncome = income,
                            primaryUseCase = "couple",
                            fcmToken = null
                        )
                    )

                    _isLoggedIn.value = true
                    _isOnboardingCompleted.value = true
                    repository.syncAll()
                    addNotification("Onboarding synced with PayTrack Server! Active primary: $name.")
                } else {
                    if (regResponse.message?.contains("Duplicate", ignoreCase = true) == true) {
                        addNotification("Email exists. Attempting Automatic Cloud Login...")
                        loginUser(email, passwordRaw)
                    } else {
                        addNotification("Onboarding Error: ${regResponse.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e("PayTrackOnboard", "Onboarding error", e)
                addNotification("Connection Error: ${e.localizedMessage}. Created Local Profile.")
                val symbol = when (currency) {
                    "PKR" -> "Rs."
                    "INR" -> "₹"
                    "EUR" -> "€"
                    "GBP" -> "£"
                    "AED" -> "د.إ"
                    else -> "$"
                }
                repository.insertUser(
                    UserProfile(
                        name = name,
                        email = email,
                        monthlyIncome = income,
                        avatarName = "Richie",
                        currencyCode = currency,
                        currencySymbol = symbol,
                        isPrimary = true
                    )
                )
                _isLoggedIn.value = true
                _isOnboardingCompleted.value = true
                repository.seedDatabase()
            }
        }
    }

    fun updateFmcToken(token: String) {
        viewModelScope.launch {
            try {
                addNotification("Syncing device push token...")
                val response = repository.apiUpdateProfile(
                    com.example.data.api.ProfileUpdateRequest(
                        name = null,
                        currency = null,
                        monthlyIncome = null,
                        primaryUseCase = null,
                        fcmToken = token
                    )
                )
                if (response.success) {
                    addNotification("FMC Device Token updated successfully!")
                } else {
                    addNotification("FMC Device Token update server error.")
                }
            } catch (e: Exception) {
                Log.e("PayTrackVM", "Failed to update FMC device token", e)
                addNotification("Could not update push token online: ${e.localizedMessage}")
            }
        }
    }

    fun updateProfile(name: String, currency: String, monthlyIncome: Double, primaryUseCase: String, fcmToken: String? = null) {
        viewModelScope.launch {
            try {
                addNotification("Syncing profile updates to cloud...")
                val response = repository.apiUpdateProfile(
                    com.example.data.api.ProfileUpdateRequest(
                        name = name,
                        currency = currency,
                        monthlyIncome = monthlyIncome,
                        primaryUseCase = primaryUseCase,
                        fcmToken = fcmToken
                    )
                )
                if (response.success) {
                    addNotification("Successfully updated profile details on PayTrack cloud!")
                    fetchServerNotifications()
                } else {
                    addNotification("Update server response issue. Keeping local settings.")
                }
            } catch (e: Exception) {
                Log.e("PayTrackVM", "Update profile failed on api", e)
                addNotification("No internet. Applied modifications offline.")
                
                // Fallback offline update
                val extProfile = primaryUser.value
                if (extProfile != null) {
                    val symbol = when (currency) {
                        "PKR" -> "Rs."
                        "INR" -> "₹"
                        "EUR" -> "€"
                        "GBP" -> "£"
                        "AED" -> "د.إ"
                        else -> "$"
                    }
                    repository.insertUser(
                        extProfile.copy(
                            name = name,
                            currencyCode = currency,
                            currencySymbol = symbol,
                            monthlyIncome = monthlyIncome
                        )
                    )
                }
            }
        }
    }

    fun fetchServerNotifications() {
        viewModelScope.launch {
            try {
                val listResponse = repository.apiFetchNotifications()
                if (listResponse.success && listResponse.data != null) {
                    _realNotifications.value = listResponse.data
                    // Check for unread alerts to notify user
                    listResponse.data.filter { !it.isRead }.forEach { item ->
                        val text = "[${item.title}] ${item.message}"
                        if (!_notifications.value.contains(text)) {
                            addNotification(text)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("PayTrackVM", "Failed to fetch cloud notifications", e)
            }
        }
    }

    // EXPENSE ACTIONS
    fun addPersonalTransaction(
        amount: Double,
        category: String,
        type: String, // "EXPENSE" or "INCOME"
        note: String,
        paymentMethod: String
    ) {
        viewModelScope.launch {
            try {
                val mappedPaymentMethod = when (paymentMethod.lowercase().trim().replace(" ", "_")) {
                    "cash" -> "cash"
                    "card", "credit", "debit" -> "card"
                    "bank", "bank_transfer", "transfer", "wire" -> "bank_transfer"
                    "e_wallet", "e-wallet", "wallet", "mobile_wallet", "digital_wallet" -> "e_wallet"
                    else -> "other"
                }
                val result = repository.apiCreateTransaction(
                    com.example.data.api.TransactionCreateRequest(
                        type = type.lowercase(),
                        amount = amount,
                        category = category,
                        paymentMethod = mappedPaymentMethod,
                        note = note,
                        pod = null,
                        splitMethod = "equal"
                    )
                )
                if (result.success) {
                    addNotification("Logged ${type.lowercase()} of $amount in $category to backend!")
                } else {
                    val tx = Transaction(
                        amount = amount,
                        category = category,
                        type = type,
                        note = note,
                        paymentMethod = mappedPaymentMethod,
                        userId = 1,
                        paidByUserId = 1
                    )
                    repository.insertTransaction(tx)
                }
            } catch (e: Exception) {
                Log.e("PayTrackVM", "Offline transaction logged locally", e)
                val mappedPaymentMethod = when (paymentMethod.lowercase().trim().replace(" ", "_")) {
                    "cash" -> "cash"
                    "card", "credit", "debit" -> "card"
                    "bank", "bank_transfer", "transfer", "wire" -> "bank_transfer"
                    "e_wallet", "e-wallet", "wallet", "mobile_wallet", "digital_wallet" -> "e_wallet"
                    else -> "other"
                }
                val tx = Transaction(
                    amount = amount,
                    category = category,
                    type = type,
                    note = note,
                    paymentMethod = mappedPaymentMethod,
                    userId = 1,
                    paidByUserId = 1
                )
                repository.insertTransaction(tx)
            }
            checkBudgetAlerts(category, amount, type)
        }
    }

    // POD COLLABORATION ACTIONS
    fun createPod(name: String, description: String, budgetAmount: Double) {
        viewModelScope.launch {
            try {
                val response = repository.apiCreatePod(
                    com.example.data.api.PodCreateRequest(name = name, budgetAmount = budgetAmount)
                )
                if (response.success && response.data != null) {
                    addNotification("Created cooperative Pod Space '${name}' successfully! Invite code: ${response.data.inviteCode}.")
                } else {
                    val code = java.util.UUID.randomUUID().toString().take(6).uppercase()
                    val newPod = Pod(
                        name = name,
                        description = description,
                        budgetAmount = budgetAmount,
                        inviteCode = code
                    )
                    repository.insertPod(newPod, emptyList())
                }
                repository.syncAll()
            } catch (e: Exception) {
                Log.e("PayTrackVM", "Pod creation network error", e)
                val code = java.util.UUID.randomUUID().toString().take(6).uppercase()
                val newPod = Pod(
                    name = name,
                    description = description,
                    budgetAmount = budgetAmount,
                    inviteCode = code
                )
                repository.insertPod(newPod, emptyList())
            }
        }
    }

    fun joinPod(inviteCode: String) {
        viewModelScope.launch {
            try {
                addNotification("Joining cloud shared cost Pod with code '$inviteCode'...")
                val response = repository.apiJoinPod(
                    com.example.data.api.PodJoinRequest(inviteCode = inviteCode)
                )
                if (response.success) {
                    addNotification("Cloud Synchronized: Joined Pod space successfully!")
                    repository.syncAll()
                } else {
                    addNotification("Failed to join Pod: ${response.message ?: "Invalid Invite Code"}")
                }
            } catch (e: Exception) {
                Log.e("PayTrackVM", "Failed to join Pod online", e)
                addNotification("Join failed: ${e.localizedMessage}. Network connection required to join collaborative cloud spaces.")
            }
        }
    }

    fun addPodTransaction(
        podId: Int,
        amount: Double,
        category: String,
        note: String,
        paidByUserId: Int,
        splitMethod: String,
        membersCount: Int,
        splits: List<com.example.data.api.TransactionSplitRequest>? = null,
        paymentMethod: String = "card"
    ) {
        viewModelScope.launch {
            try {
                val pods = repository.allPods.firstOrNull() ?: emptyList()
                val targetPod = pods.find { it.id == podId }
                val remotePodId = targetPod?.let { repository.getRemotePodId(it) }

                val mappedPaymentMethod = when (paymentMethod.lowercase().trim().replace(" ", "_")) {
                    "cash" -> "cash"
                    "card", "credit", "debit" -> "card"
                    "bank", "bank_transfer", "transfer", "wire" -> "bank_transfer"
                    "e_wallet", "e-wallet", "wallet", "mobile_wallet", "digital_wallet" -> "e_wallet"
                    else -> "other"
                }

                if (remotePodId != null) {
                    addNotification("Submitting collaborative expense to cloud...")
                    val result = repository.apiCreateTransaction(
                        com.example.data.api.TransactionCreateRequest(
                            type = "expense",
                            amount = amount,
                            category = category,
                            paymentMethod = mappedPaymentMethod,
                            note = note,
                            pod = remotePodId,
                            splitMethod = splitMethod.lowercase(),
                            splits = splits
                        )
                    )
                    if (result.success) {
                        addNotification("Pod expense logged online across all spaces & synchronized!")
                        repository.syncAll()
                        return@launch
                    }
                }
            } catch (e: Exception) {
                Log.e("PayTrackVM", "Failed to add pod transaction on server", e)
            }

            val perMemberShare = amount / if (membersCount > 0) membersCount else 1
            val splitDetailsBuilder = StringBuilder()
            if (splits != null && splitMethod.uppercase() == "EXACT") {
                splits.forEachIndexed { index, sItem ->
                    splitDetailsBuilder.append("${index + 1}:${sItem.amount}")
                    if (index < splits.size - 1) splitDetailsBuilder.append(",")
                }
            } else {
                for (i in 1..membersCount) {
                    splitDetailsBuilder.append("$i:$perMemberShare")
                    if (i < membersCount) splitDetailsBuilder.append(",")
                }
            }

            val tx = Transaction(
                amount = amount,
                category = category,
                type = "EXPENSE",
                note = note,
                podId = podId,
                paidByUserId = paidByUserId,
                splitMethod = splitMethod,
                splitDetails = splitDetailsBuilder.toString(),
                paymentMethod = paymentMethod
            )
            repository.insertTransaction(tx)
            addNotification("Safe Fallback: Split $amount ($splitMethod) to local pod storage.")
        }
    }

    fun uploadReceipt(context: android.content.Context, uri: android.net.Uri, onSuccess: (String) -> Unit) {
        viewModelScope.launch {
            try {
                addNotification("Uploading receipt file to cloud backend...")
                val contentResolver = context.contentResolver
                val mimeType = contentResolver.getType(uri) ?: "image/jpeg"
                val extension = when {
                    mimeType.contains("pdf") -> "pdf"
                    mimeType.contains("png") -> "png"
                    else -> "jpg"
               }
               val inputStream = contentResolver.openInputStream(uri)
               if (inputStream == null) {
                   addNotification("Error: Could not read file stream")
                   return@launch
               }
               val tempFile = java.io.File.createTempFile("receipt_", ".$extension", context.cacheDir)
               tempFile.outputStream().use { outputStream ->
                   inputStream.copyTo(outputStream)
               }
               val requestFile = okhttp3.RequestBody.create(
                   mimeType.toMediaTypeOrNull(),
                   tempFile
               )
               val bodyPart = okhttp3.MultipartBody.Part.createFormData("receipt", tempFile.name, requestFile)
               val response = repository.apiUploadReceipt(bodyPart)
               if (response.success && response.receiptUrl != null) {
                   addNotification("📄 Receipt uploaded successfully: ${response.filename}")
                   onSuccess(response.receiptUrl)
               } else {
                   addNotification("Upload failed: ${response.message ?: "Server Error"}")
               }
            } catch (e: Exception) {
               Log.e("PayTrackVM", "Failed to upload receipt", e)
               addNotification("Upload failed: ${e.localizedMessage}")
            }
        }
    }

    fun simulateRemoteMemberTransaction(podId: Int, podName: String) {
        viewModelScope.launch {
            val mockMembers = listOf("Ahmed Al-Farsi", "Daisie Wright", "Bilal Khan")
            val selectedMember = mockMembers.random()
            val memberId = when(selectedMember) {
                "Ahmed Al-Farsi" -> 2
                "Daisie Wright" -> 3
                else -> 4
            }
            val amount = (20..150).random().toDouble()
            val cat = listOf("Food", "Groceries", "Transport", "Entertainment").random()
            val noteDesc = when(cat) {
                "Food" -> "Dinner split at Pizza restaurant"
                "Groceries" -> "Beverages and organic grocery stock"
                "Transport" -> "Joint taxi ride to hotel"
                else -> "Movie theater flat fee tickets"
            }

            // equal split
            val perMemberShare = amount / 4
            val splitDetails = "1:$perMemberShare,2:$perMemberShare,3:$perMemberShare,4:$perMemberShare"

            val tx = Transaction(
                userId = memberId,
                podId = podId,
                type = "EXPENSE",
                amount = amount,
                category = cat,
                paymentMethod = "Mobile Wallet",
                note = "$selectedMember: $noteDesc",
                paidByUserId = memberId,
                splitMethod = "EQUAL",
                splitDetails = splitDetails
            )
            repository.insertTransaction(tx)
            addNotification("[🔔 Pod Activity] $selectedMember added flat expense '$noteDesc' for $amount inside '$podName'!")
        }
    }

    fun resolveSettlement(podId: Int, fromUser: Int, toUser: Int, amount: Double) {
        viewModelScope.launch {
            try {
                val pods = repository.allPods.firstOrNull() ?: emptyList()
                val targetPod = pods.find { it.id == podId }
                val remotePodId = targetPod?.let { repository.getRemotePodId(it) }

                if (remotePodId != null) {
                    addNotification("Sending Settle-up request to server...")
                    val result = com.example.data.api.PayTrackApiClient.apiService.settlePodDebt(
                        "Bearer ${repository.sessionManager.token ?: ""}",
                        remotePodId,
                        com.example.data.api.PodSettleRequest(
                            toUserId = toUser.toString(),
                            amount = amount,
                            paymentMethod = "Mobile Wallet"
                        )
                    )
                    if (result.success) {
                        addNotification("[🤝 Settle Up] Pairwise balance settled on PayTrack Cloud!")
                        repository.syncAll()
                        return@launch
                    }
                }
            } catch (e: Exception) {
                Log.e("PayTrackVM", "Settle failed on api", e)
            }

            val userNames = mapOf(1 to "You", 2 to "Ahmed Al-Farsi", 3 to "Daisie Wright", 4 to "Bilal Khan")
            val tx = Transaction(
                userId = fromUser,
                podId = podId,
                type = "INCOME",
                amount = amount,
                category = "Settlement",
                note = "Settle Debt: ${userNames[fromUser]} paid ${userNames[toUser]} Rs. $amount",
                paidByUserId = fromUser,
                splitMethod = "EXACT",
                splitDetails = "$toUser:-$amount"
            )
            repository.insertTransaction(tx)
            addNotification("[🤝 Settle Up] Settle registered locally in Pod space: Rs. $amount.")
        }
    }

    // BILL REMINDERS
    fun addBill(name: String, amount: Double, dueDateOffsetDays: Int, recurrence: String, category: String, podIdString: String? = null) {
        viewModelScope.launch {
            val dueDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(System.currentTimeMillis() + (dueDateOffsetDays * 1000L * 60 * 60 * 24)))
            var createdRemoteId: String? = null
            try {
                val result = repository.apiCreateBill(
                    com.example.data.api.BillCreateRequest(
                        name = name,
                        amount = amount,
                        dueDate = dueDateStr,
                        recurrence = recurrence,
                        category = category,
                        reminderDaysBefore = 5,
                        pod = podIdString
                    )
                )
                if (result.success && result.data != null) {
                    createdRemoteId = result.data._id
                    addNotification("Cloud Synchronized: saved Bill reminder '$name' successfully!")
                    return@launch
                }
            } catch (e: Exception) {
                Log.e("PayTrackVM", "Failed to add bill reminder", e)
            }

            val dueDate = System.currentTimeMillis() + (dueDateOffsetDays * 1000L * 60 * 60 * 24)
            val newBill = Bill(
                name = name,
                amount = amount,
                dueDate = dueDate,
                recurrence = recurrence,
                category = category,
                isPaid = false,
                remoteId = createdRemoteId
            )
            repository.insertBill(newBill)
            addNotification("Saved bill '$name' locally (due in $dueDateOffsetDays days).")
        }
    }

    fun markBillAsPaid(bill: Bill) {
        viewModelScope.launch {
            try {
                if (bill.remoteId != null) {
                    addNotification("Sending bill payment request to server...")
                    val response = repository.apiPayBill(bill.remoteId)
                    if (response.success) {
                        val updated = bill.copy(isPaid = true, paidAt = System.currentTimeMillis())
                        repository.updateBill(updated)
                        addNotification("Bill '${bill.name}' marked as PAID on server. Auto-logged split transaction synchronized!")
                        return@launch
                    }
                }
            } catch (e: Exception) {
                Log.e("PayTrackVM", "apiPayBill call failed, falling back to local flow", e)
            }

            // Fallback
            val updated = bill.copy(isPaid = true, paidAt = System.currentTimeMillis())
            repository.updateBill(updated)

            // Auto log as corresponding personal expense
            val tx = Transaction(
                userId = 1,
                type = "EXPENSE",
                amount = bill.amount,
                category = bill.category,
                note = "Auto-logged Bill Payment: ${bill.name}",
                paymentMethod = "Bank Wallet"
            )
            repository.insertTransaction(tx)
            addNotification("Bill '${bill.name}' marked as PAID locally. Automatically registered local expense.")
        }
    }

    // BUDGET LIMIT CHECKERS
    private fun checkBudgetAlerts(category: String, amount: Double, txType: String) {
        if (txType != "EXPENSE") return
        viewModelScope.launch {
            val budgets = allBudgets.value
            val txs = allTransactions.value

            // Calc sum spent this month
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.DAY_OF_MONTH, 1)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            val startOfMonth = calendar.timeInMillis

            val totalSpentCategory = txs.filter { 
                it.type == "EXPENSE" && it.category == category && it.date >= startOfMonth 
            }.sumOf { it.amount }

            val totalSpentOverall = txs.filter {
                it.type == "EXPENSE" && it.date >= startOfMonth
            }.sumOf { it.amount }

            budgets.forEach { budget ->
                if (budget.category == "Overall") {
                    val progress = totalSpentOverall / budget.limitAmount
                    if (progress >= 1.0) {
                        addNotification("[⚠️ Budget Exceeded] Spent overall ($totalSpentOverall) exceeds budget threshold of ${budget.limitAmount}!")
                    } else if (progress >= 0.8) {
                        addNotification("[⚠️ Low Balance Warning] Remaining overall budget is under 20% (${budget.limitAmount - totalSpentOverall} left)!")
                    }
                } else if (budget.category == category) {
                    val progress = totalSpentCategory / budget.limitAmount
                    if (progress >= 1.0) {
                        addNotification("[⚠️ Budget Alert] Specified category limits broken: $category spend is at $totalSpentCategory / ${budget.limitAmount}!")
                    }
                }
            }
        }
    }

    // REVENUE CHARTS HELPER
    fun getCategorySplits(): Map<String, Double> {
        val txs = allTransactions.value.filter { it.type == "EXPENSE" }
        val categorySum = txs.groupBy { it.category }.mapValues { entry ->
            entry.value.sumOf { it.amount }
        }
        return categorySum.ifEmpty { mapOf("Other" to 0.0) }
    }

    // EXPORTS UTILITY
    fun exportReport(context: Context, format: String): String {
        // Generating CSV/XLSX text representation locally in storage
        val sdf = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
        val timestamp = sdf.format(Date())
        val fileName = "paytrack_report_$timestamp.${if (format == "Excel") "csv" else "txt"}"
        val filesDir = context.cacheDir
        val file = File(filesDir, fileName)

        try {
            val writer = FileWriter(file)
            if (format == "Excel") {
                // Compile CSV rows
                writer.append("ID,Type,Category,Amount,Note,Date,PaymentMethod\n")
                allTransactions.value.forEach { tx ->
                    val txDate = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(tx.date))
                    writer.append("${tx.id},${tx.type},${tx.category},${tx.amount},\"${tx.note}\",$txDate,${tx.paymentMethod}\n")
                }
            } else {
                // Formatted text representing a high-fidelity PDF invoice/statement layout
                writer.append("=========================================\n")
                writer.append("            PAYTRACK FINANCIAL REPORT    \n")
                writer.append("=========================================\n")
                writer.append("Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())}\n")
                writer.append("User: ${primaryUser.value?.name ?: "Richie Jimenez"}\n")
                writer.append("Currency: ${primaryUser.value?.currencySymbol ?: "$"}\n")
                writer.append("-----------------------------------------\n\n")

                val totalIncome = allTransactions.value.filter { it.type == "INCOME" }.sumOf { it.amount }
                val totalExpense = allTransactions.value.filter { it.type == "EXPENSE" }.sumOf { it.amount }

                writer.append("SUMMARY STATS\n")
                writer.append("Total Income: ${primaryUser.value?.currencySymbol ?: "$"}$totalIncome\n")
                writer.append("Total Expense: ${primaryUser.value?.currencySymbol ?: "$"}$totalExpense\n")
                writer.append("Net Balance: ${primaryUser.value?.currencySymbol ?: "$"}${totalIncome - totalExpense}\n\n")

                writer.append("DETAILED FEED LEDGER:\n")
                allTransactions.value.forEach { tx ->
                    val txDate = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(tx.date))
                    writer.append("[$txDate] ${tx.type} | ${tx.category} | ${primaryUser.value?.currencySymbol ?: "$"}${tx.amount} | Note: ${tx.note}\n")
                }
                writer.append("\n=========================================\nEnd of Report.")
            }
            writer.flush()
            writer.close()

            // Open share sheet immediately!
            val uri: Uri = FileProvider.getUriForFile(context, "com.example.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = if (format == "Excel") "text/csv" else "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "PayTrack Financial Report")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share PayTrack Report"))

            return "Report Generated successfully in storage! File shared."
        } catch (e: Exception) {
            Log.e("PayTrackExport", "Error writing file report", e)
            return "Export failed: ${e.localizedMessage}"
        }
    }

    // Helper Flow and command bindings for View layouts
    fun getTransactionsByPod(podId: Int): Flow<List<Transaction>> = repository.getTransactionsByPod(podId)

    fun getPodMembers(podId: Int): Flow<List<PodMember>> = repository.getPodMembers(podId)

    fun insertBudget(budget: Budget) {
        viewModelScope.launch {
            try {
                val response = repository.apiCreateBudget(
                    com.example.data.api.BudgetCreateRequest(
                        limitAmount = budget.limitAmount,
                        category = budget.category,
                        lowBalanceThresholdPercent = budget.thresholdPercent,
                        startDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
                        endDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000))
                    )
                )
                if (response.success) {
                    addNotification("Cloud Limit configured for ${budget.category} at ${budget.limitAmount}!")
                    return@launch
                }
            } catch (e: Exception) {
                Log.e("PayTrackVM", "Api insertBudget failure", e)
            }
            repository.insertBudget(budget)
        }
    }

    fun createBudget(category: String, limitAmount: Double, thresholdPercent: Int, startDate: String, endDate: String) {
        viewModelScope.launch {
            try {
                addNotification("Creating budget for category '$category' on server...")
                val response = repository.apiCreateBudget(
                    com.example.data.api.BudgetCreateRequest(
                        limitAmount = limitAmount,
                        category = category,
                        lowBalanceThresholdPercent = thresholdPercent,
                        startDate = startDate,
                        endDate = endDate
                    )
                )
                if (response.success) {
                    addNotification("Budget for '$category' created successfully on Cloud Server!")
                    repository.syncBudgets()
                    return@launch
                }
            } catch (e: Exception) {
                Log.e("PayTrackVM", "createBudget API failed, falling back to local simulation", e)
            }
            // fallback
            val b = Budget(
                category = category,
                limitAmount = limitAmount,
                thresholdPercent = thresholdPercent,
                spentAmount = 0.0,
                remainingAmount = limitAmount,
                percentSpent = 0.0,
                isOverspent = false
            )
            repository.insertBudget(b)
            addNotification("Created budget for '$category' locally.")
        }
    }

    fun deleteTransactionById(id: Int) {
        viewModelScope.launch {
            repository.deleteTransactionById(id)
        }
    }
}
