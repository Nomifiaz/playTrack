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
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

class PayTrackViewModel(private val repository: AppRepository) : ViewModel() {

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

    fun setTab(tabName: String) {
        _currentTab.value = tabName
        if (tabName != "pods_detail") {
            _selectedPodId.value = null
        }
    }

    fun selectPod(podId: Int) {
        _selectedPodId.value = podId
        _currentTab.value = "pods_detail"
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

    fun completeOnboarding(name: String, currency: String, income: Double) {
        viewModelScope.launch {
            try {
                addNotification("Configuring cloud space profile...")
                val regResponse = repository.apiRegister(
                    com.example.data.api.RegisterRequest(
                        name = name,
                        email = "${name.lowercase().replace(" ", "")}@builder.warriach.online",
                        password = "securepassword123",
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
                    simulateLogin("${name.lowercase().replace(" ", "")}@builder.warriach.online", name)
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
                        email = "user@example.com",
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
                val result = repository.apiCreateTransaction(
                    com.example.data.api.TransactionCreateRequest(
                        type = type.lowercase(),
                        amount = amount,
                        category = category,
                        paymentMethod = paymentMethod,
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
                        paymentMethod = paymentMethod,
                        userId = 1,
                        paidByUserId = 1
                    )
                    repository.insertTransaction(tx)
                }
            } catch (e: Exception) {
                Log.e("PayTrackVM", "Offline transaction logged locally", e)
                val tx = Transaction(
                    amount = amount,
                    category = category,
                    type = type,
                    note = note,
                    paymentMethod = paymentMethod,
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

    fun addPodTransaction(
        podId: Int,
        amount: Double,
        category: String,
        note: String,
        paidByUserId: Int,
        splitMethod: String,
        membersCount: Int
    ) {
        viewModelScope.launch {
            try {
                val pods = repository.allPods.firstOrNull() ?: emptyList()
                val targetPod = pods.find { it.id == podId }
                val remotePodId = targetPod?.let { repository.getRemotePodId(it) }

                if (remotePodId != null) {
                    addNotification("Submitting collaborative expense to cloud...")
                    val result = repository.apiCreateTransaction(
                        com.example.data.api.TransactionCreateRequest(
                            type = "expense",
                            amount = amount,
                            category = category,
                            paymentMethod = "Mobile Wallet",
                            note = note,
                            pod = remotePodId,
                            splitMethod = splitMethod.lowercase()
                        )
                    )
                    if (result.success) {
                        addNotification("Pod expense logged online across all spaces & synchronized!")
                        return@launch
                    }
                }
            } catch (e: Exception) {
                Log.e("PayTrackVM", "Failed to add pod transaction on server", e)
            }

            val perMemberShare = amount / if (membersCount > 0) membersCount else 1
            val splitDetailsBuilder = StringBuilder()
            for (i in 1..membersCount) {
                splitDetailsBuilder.append("$i:$perMemberShare")
                if (i < membersCount) splitDetailsBuilder.append(",")
            }

            val tx = Transaction(
                amount = amount,
                category = category,
                type = "EXPENSE",
                note = note,
                podId = podId,
                paidByUserId = paidByUserId,
                splitMethod = splitMethod,
                splitDetails = splitDetailsBuilder.toString()
            )
            repository.insertTransaction(tx)
            addNotification("Safe Fallback: Split $amount equally to local pod storage.")
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
    fun addBill(name: String, amount: Double, dueDateOffsetDays: Int, recurrence: String, category: String) {
        viewModelScope.launch {
            val dueDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(System.currentTimeMillis() + (dueDateOffsetDays * 1000L * 60 * 60 * 24)))
            try {
                val result = repository.apiCreateBill(
                    com.example.data.api.BillCreateRequest(
                        name = name,
                        amount = amount,
                        dueDate = dueDateStr,
                        recurrence = recurrence,
                        category = category,
                        reminderDaysBefore = 1,
                        pod = null
                    )
                )
                if (result.success) {
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
                isPaid = false
            )
            repository.insertBill(newBill)
            addNotification("Saved bill '$name' locally (due in $dueDateOffsetDays days).")
        }
    }

    fun markBillAsPaid(bill: Bill) {
        viewModelScope.launch {
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
            addNotification("Internet bill of ${bill.amount} marked as PAID. Automatically registered expense.")
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

    fun deleteTransactionById(id: Int) {
        viewModelScope.launch {
            repository.deleteTransactionById(id)
        }
    }
}
