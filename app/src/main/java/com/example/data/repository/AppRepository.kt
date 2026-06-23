package com.example.data.repository

import android.util.Log
import com.example.data.dao.*
import com.example.data.model.*
import com.example.data.api.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import java.text.SimpleDateFormat
import java.util.Locale

class AppRepository(
    private val userProfileDao: UserProfileDao,
    private val podDao: PodDao,
    private val transactionDao: TransactionDao,
    private val billDao: BillDao,
    private val budgetDao: BudgetDao,
    val sessionManager: SessionManager
) {
    val primaryUser: Flow<UserProfile?> = userProfileDao.getPrimaryUser()
    val allUsers: Flow<List<UserProfile>> = userProfileDao.getAllUsers()
    
    val allPods: Flow<List<Pod>> = podDao.getAllPods()
    fun getPodById(podId: Int): Flow<Pod?> = podDao.getPodById(podId)
    fun getPodMembers(podId: Int): Flow<List<PodMember>> = podDao.getPodMembers(podId)

    val allTransactions: Flow<List<Transaction>> = transactionDao.getAllTransactions()
    val personalTransactions: Flow<List<Transaction>> = transactionDao.getPersonalTransactions()
    fun getTransactionsByPod(podId: Int): Flow<List<Transaction>> = transactionDao.getTransactionsByPod(podId)

    val allBills: Flow<List<Bill>> = billDao.getAllBills()
    val upcomingBills: Flow<List<Bill>> = billDao.getUpcomingBills()

    val allBudgets: Flow<List<Budget>> = budgetDao.getAllBudgets()

    suspend fun getUserProfileById(id: Int): UserProfile? = userProfileDao.getUserById(id)

    suspend fun insertUser(user: UserProfile) = userProfileDao.insertUser(user)
    suspend fun updateUser(user: UserProfile) = userProfileDao.updateUser(user)
    suspend fun deleteUser(user: UserProfile) = userProfileDao.deleteUser(user)

    suspend fun insertPod(pod: Pod, members: List<UserProfile>): Int {
        val podId = podDao.insertPod(pod).toInt()
        members.forEach { member ->
            podDao.insertPodMember(
                PodMember(
                    podId = podId,
                    userId = member.id,
                    userName = member.name,
                    avatarName = member.avatarName
                )
            )
        }
        return podId
    }

    suspend fun addMemberToPod(podId: Int, user: UserProfile) {
        podDao.insertPodMember(
            PodMember(
                podId = podId,
                userId = user.id,
                userName = user.name,
                avatarName = user.avatarName
            )
        )
    }

    suspend fun deletePod(pod: Pod) {
        podDao.deleteMembersByPodId(pod.id)
        podDao.deletePod(pod)
    }

    suspend fun insertTransaction(transaction: Transaction): Int {
        return transactionDao.insertTransaction(transaction).toInt()
    }

    suspend fun deleteTransaction(transaction: Transaction) {
        transactionDao.deleteTransaction(transaction)
    }

    suspend fun deleteTransactionById(id: Int) {
        transactionDao.deleteTransactionById(id)
    }

    suspend fun insertBill(bill: Bill) = billDao.insertBill(bill)
    suspend fun updateBill(bill: Bill) = billDao.updateBill(bill)
    suspend fun deleteBill(bill: Bill) = billDao.deleteBill(bill)

    suspend fun insertBudget(budget: Budget) = budgetDao.insertBudget(budget)
    suspend fun updateBudget(budget: Budget) = budgetDao.updateBudget(budget)
    suspend fun deleteBudget(budget: Budget) = budgetDao.deleteBudget(budget)

    suspend fun clearAllData() {
        userProfileDao.clearAllUsers()
        transactionDao.clearAllTransactions()
        billDao.clearAllBills()
        budgetDao.clearAllBudgets()
    }

    /**
     * Seeds the local database with realistic sample transactions, bills, and pods
     * representation to match initial UI screen flows and the visual layout in screenshots.
     */
    suspend fun seedDatabase() {
        val currentUsers = allUsers.firstOrNull()
        if (currentUsers.isNullOrEmpty()) {
            Log.d("AppRepository", "Seeding database with default mock data...")

            // 1. Create Default Users
            val primaryId = userProfileDao.insertUser(
                UserProfile(
                    name = "Richie Jimenez",
                    email = "richie.j@example.com",
                    monthlyIncome = 6500.00,
                    avatarName = "Richie",
                    currencyCode = "USD",
                    currencySymbol = "$",
                    isPrimary = true
                )
            ).toInt()

            val ahmedId = userProfileDao.insertUser(
                UserProfile(name = "Ahmed Al-Farsi", email = "ahmed.f@example.com", avatarName = "Ahmed")
            ).toInt()

            val daisieId = userProfileDao.insertUser(
                UserProfile(name = "Daisie Wright", email = "daisie.w@example.com", avatarName = "Daisie")
            ).toInt()

            val bilalId = userProfileDao.insertUser(
                UserProfile(name = "Bilal Khan", email = "bilal.k@example.com", avatarName = "Bilal")
            ).toInt()

            // Retrieve populated instances
            val richie = userProfileDao.getUserById(primaryId) ?: return
            val ahmed = userProfileDao.getUserById(ahmedId) ?: return
            val daisie = userProfileDao.getUserById(daisieId) ?: return
            val bilal = userProfileDao.getUserById(bilalId) ?: return

            // 2. Add Personal Transactions (exactly from the reference design image!)
            val now = System.currentTimeMillis()
            transactionDao.insertTransaction(
                Transaction(
                    userId = primaryId,
                    type = "EXPENSE",
                    amount = 21.00,
                    category = "Transport",
                    date = now - 1000 * 60 * 15, // 15 mins ago
                    paymentMethod = "Card",
                    note = "Uber - ride back from meeting",
                    paidByUserId = primaryId
                )
            )

            transactionDao.insertTransaction(
                Transaction(
                    userId = primaryId,
                    type = "INCOME",
                    amount = 14.00,
                    category = "Refund",
                    date = now - 1000 * 60 * 60 * 5, // 5 hours ago
                    paymentMethod = "Wallet",
                    note = "Apple Subscription refund (error charge)",
                    paidByUserId = primaryId
                )
            )

            transactionDao.insertTransaction(
                Transaction(
                    userId = primaryId,
                    type = "EXPENSE",
                    amount = 100.00,
                    category = "Groceries",
                    date = now - 1000 * 60 * 60 * 24, // Yesterday
                    paymentMethod = "Card",
                    note = "Weekly groceries at Trader Joe's",
                    paidByUserId = primaryId
                )
            )

            transactionDao.insertTransaction(
                Transaction(
                    userId = primaryId,
                    type = "EXPENSE",
                    amount = 12.50,
                    category = "Food",
                    date = now - 1000 * 60 * 60 * 36, // 1.5 days ago
                    paymentMethod = "Cash",
                    note = "Fresh Brewed Coffee & Pastry",
                    paidByUserId = primaryId
                )
            )

            // 3. Create Collaborative Pods
            val pod1Id = podDao.insertPod(
                Pod(
                    name = "Roommates Flat 4B",
                    description = "Splits for rent, electricity, Wi-Fi router, and weekly utilities.",
                    creatorId = primaryId,
                    inviteCode = "FLAT4B",
                    budgetAmount = 2000.00
                )
            ).toInt()

            // Populate Pod 1 members
            podDao.insertPodMember(PodMember(podId = pod1Id, userId = primaryId, userName = richie.name, avatarName = "Richie"))
            podDao.insertPodMember(PodMember(podId = pod1Id, userId = ahmedId, userName = ahmed.name, avatarName = "Ahmed"))
            podDao.insertPodMember(PodMember(podId = pod1Id, userId = daisieId, userName = daisie.name, avatarName = "Daisie"))
            podDao.insertPodMember(PodMember(podId = pod1Id, userId = bilalId, userName = bilal.name, avatarName = "Bilal"))

            // Seed Group Shared Expense (Ahmed paid 1200.00 rent, Richie owes 300.00, and wifi internet router)
            transactionDao.insertTransaction(
                Transaction(
                    userId = ahmedId,
                    podId = pod1Id,
                    type = "EXPENSE",
                    amount = 1200.00,
                    category = "Rent",
                    date = now - 1000 * 60 * 60 * 48, // 2 days ago
                    paymentMethod = "Bank",
                    note = "Monthly Rent 4B flat",
                    paidByUserId = ahmedId,
                    splitMethod = "EQUAL",
                    splitDetails = "1:300.0,2:300.0,3:300.0,4:300.0"
                )
            )

            transactionDao.insertTransaction(
                Transaction(
                    userId = primaryId,
                    podId = pod1Id,
                    type = "EXPENSE",
                    amount = 80.00,
                    category = "Bills",
                    date = now - 1000 * 60 * 60 * 24 * 3, // 3 days ago
                    paymentMethod = "Card",
                    note = "Wi-Fi Router Router Bill",
                    paidByUserId = primaryId,
                    splitMethod = "EQUAL",
                    splitDetails = "1:20.0,2:20.0,3:20.0,4:20.0"
                )
            )

            val pod2Id = podDao.insertPod(
                Pod(
                    name = "Goa Trip 2026",
                    description = "Joint trip with Daisie & Bilal: flights, beach snacks, and travel.",
                    creatorId = primaryId,
                    inviteCode = "GOASUMMER",
                    budgetAmount = 800.00
                )
            ).toInt()

            // Populate Pod 2 members
            podDao.insertPodMember(PodMember(podId = pod2Id, userId = primaryId, userName = richie.name, avatarName = "Richie"))
            podDao.insertPodMember(PodMember(podId = pod2Id, userId = daisieId, userName = daisie.name, avatarName = "Daisie"))
            podDao.insertPodMember(PodMember(podId = pod2Id, userId = bilalId, userName = bilal.name, avatarName = "Bilal"))

            transactionDao.insertTransaction(
                Transaction(
                    userId = daisieId,
                    podId = pod2Id,
                    type = "EXPENSE",
                    amount = 180.00,
                    category = "Transport",
                    date = now - 1000 * 60 * 60 * 72,
                    paymentMethod = "Card",
                    note = "Jet Ski Ride and Beach boards",
                    paidByUserId = daisieId,
                    splitMethod = "EQUAL",
                    splitDetails = "1:60.0,3:60.0,4:60.0"
                )
            )

            // 4. Create bills & reminders
            billDao.insertBill(
                Bill(
                    userId = primaryId,
                    name = "Room rent subscription",
                    amount = 450.00,
                    dueDate = now - 1000 * 60 * 60 * 12, // OVERDUE 12 hours
                    recurrence = "Monthly",
                    category = "Rent",
                    isPaid = false
                )
            )

            billDao.insertBill(
                Bill(
                    userId = primaryId,
                    name = "High-speed Internet",
                    amount = 49.99,
                    dueDate = now + 1000 * 60 * 60 * 24 * 3, // Due in 3 days
                    recurrence = "Monthly",
                    category = "Bills",
                    isPaid = false
                )
            )

            billDao.insertBill(
                Bill(
                    userId = primaryId,
                    name = "AWS Server Cloud Cloud",
                    amount = 12.50,
                    dueDate = now + 1000 * 60 * 60 * 24 * 7, // Due in 7 days
                    recurrence = "Monthly",
                    category = "Other",
                    isPaid = false
                )
            )

            // 5. Create budgets
            budgetDao.insertBudget(Budget(userId = primaryId, category = "Overall", limitAmount = 2500.00))
            budgetDao.insertBudget(Budget(userId = primaryId, category = "Food", limitAmount = 400.00))
            budgetDao.insertBudget(Budget(userId = primaryId, category = "Bills", limitAmount = 600.00))
            budgetDao.insertBudget(Budget(userId = primaryId, category = "Groceries", limitAmount = 500.00))
        }
    }

    // --- API SYNCHRONIZATION AND INTEGRATION METHODS ---

    private fun getAuthHeader(): String = "Bearer ${sessionManager.token ?: ""}"

    suspend fun syncAll(): Boolean {
        val token = sessionManager.token ?: return false
        try {
            syncProfile()
            syncTransactions()
            syncBudgets()
            return true
        } catch (e: Exception) {
            Log.e("AppRepository", "syncAll failed", e)
        }
        return false
    }

    suspend fun syncProfile(): Boolean {
        val token = sessionManager.token ?: return false
        try {
            val response = PayTrackApiClient.apiService.getUserProfile("Bearer $token")
            if (response.success && response.data != null) {
                val user = response.data
                val symbol = when (user.currency) {
                    "PKR" -> "Rs."
                    "INR" -> "₹"
                    "EUR" -> "€"
                    "GBP" -> "£"
                    "AED" -> "د.إ"
                    else -> "$"
                }
                val cachedUser = UserProfile(
                    id = 1,
                    name = user.name,
                    email = user.email,
                    currencyCode = user.currency,
                    currencySymbol = symbol,
                    monthlyIncome = user.monthlyIncome ?: 0.0,
                    isPrimary = true
                )
                userProfileDao.clearAllUsers()
                userProfileDao.insertUser(cachedUser)
                sessionManager.userName = user.name
                sessionManager.userEmail = user.email
                sessionManager.currency = user.currency
                return true
            }
        } catch (e: Exception) {
            Log.e("AppRepository", "syncProfile error", e)
        }
        return false
    }

    suspend fun syncTransactions(): Boolean {
        val token = sessionManager.token ?: return false
        try {
            val response = PayTrackApiClient.apiService.getTransactions("Bearer $token", null, null, null, null)
            if (response.success && response.data != null) {
                transactionDao.clearAllTransactions()
                response.data.forEach { feedTx ->
                    val localPodId = feedTx.pod?.hashCode()?.let { it and 0x7FFFFFFF }
                    val tx = Transaction(
                        id = feedTx._id.hashCode() and 0x7FFFFFFF,
                        userId = if (feedTx.user?._id == sessionManager.userId) 1 else (feedTx.user?._id?.hashCode()?.let { it and 0x7FFFFFFF } ?: 2),
                        podId = localPodId,
                        type = feedTx.type.uppercase(),
                        amount = feedTx.amount,
                        category = feedTx.category,
                        date = try {
                            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
                            sdf.parse(feedTx.date)?.time ?: System.currentTimeMillis()
                        } catch (e: Exception) {
                            System.currentTimeMillis()
                        },
                        note = feedTx.note ?: feedTx.category,
                        paidByUserId = if (feedTx.user?._id == sessionManager.userId) 1 else 2
                    )
                    transactionDao.insertTransaction(tx)
                }
                return true
            }
        } catch (e: Exception) {
            Log.e("AppRepository", "syncTransactions error", e)
        }
        return false
    }

    suspend fun syncBudgets(): Boolean {
        val token = sessionManager.token ?: return false
        try {
            val response = PayTrackApiClient.apiService.getBudgetsProgress("Bearer $token")
            if (response.success && response.data != null) {
                budgetDao.clearAllBudgets()
                response.data.forEach { budgetData ->
                    val b = Budget(
                        id = budgetData.budgetId.hashCode() and 0x7FFFFFFF,
                        userId = 1,
                        category = budgetData.category,
                        limitAmount = budgetData.limitAmount,
                        thresholdPercent = budgetData.lowBalanceThresholdPercent
                    )
                    budgetDao.insertBudget(b)
                }
                return true
            }
        } catch (e: Exception) {
            Log.e("AppRepository", "syncBudgets error", e)
        }
        return false
    }

    suspend fun apiRegister(request: RegisterRequest): AuthResponse {
        return PayTrackApiClient.apiService.register(request)
    }

    suspend fun apiLogin(email: String, passwordRaw: String): AuthResponse {
        // Correct request body
        val body = LoginBody(email, passwordRaw)
        return PayTrackApiClient.apiService.login(body)
    }

    suspend fun apiUpdateProfile(request: ProfileUpdateRequest): ProfileResponse {
        val response = PayTrackApiClient.apiService.updateProfile(getAuthHeader(), request)
        if (response.success) {
            syncProfile()
        }
        return response
    }

    fun getRemotePodId(pod: Pod): String? {
        if (pod.description.contains("[REMOTE_ID:")) {
            return pod.description.substringAfter("[REMOTE_ID:").substringBefore("]")
        }
        return null
    }

    suspend fun apiCreatePod(request: PodCreateRequest): PodCreateResponse {
        val response = PayTrackApiClient.apiService.createPod(getAuthHeader(), request)
        if (response.success && response.data != null) {
            val remotePod = response.data
            val localPod = Pod(
                id = remotePod._id.hashCode() and 0x7FFFFFFF,
                name = remotePod.name,
                description = "[REMOTE_ID:${remotePod._id}] Collaborative Space",
                creatorId = 1,
                inviteCode = remotePod.inviteCode,
                budgetAmount = remotePod.budgetAmount,
                currencyCode = sessionManager.currency ?: "USD"
            )
            podDao.insertPod(localPod)
            // also insert self as first member
            podDao.insertPodMember(
                PodMember(
                    podId = localPod.id,
                    userId = 1,
                    userName = sessionManager.userName ?: "You",
                    avatarName = "member_avatar"
                )
            )
        }
        return response
    }

    suspend fun apiJoinPod(request: PodJoinRequest): PodJoinResponse {
        val response = PayTrackApiClient.apiService.joinPod(getAuthHeader(), request)
        if (response.success && response.data != null) {
            val remotePod = response.data
            val localPod = Pod(
                id = remotePod._id.hashCode() and 0x7FFFFFFF,
                name = remotePod.name,
                description = "[REMOTE_ID:${remotePod._id}] Joined Space",
                creatorId = 2,
                inviteCode = request.inviteCode,
                budgetAmount = 0.0,
                currencyCode = sessionManager.currency ?: "USD"
            )
            podDao.insertPod(localPod)
            // Populate members
            remotePod.members.forEachIndexed { i, memberId ->
                podDao.insertPodMember(
                    PodMember(
                        podId = localPod.id,
                        userId = memberId.hashCode() and 0x7FFFFFFF,
                        userName = if (memberId == sessionManager.userId) (sessionManager.userName ?: "You") else "Member ${i + 1}",
                        avatarName = "member_avatar"
                    )
                )
            }
        }
        return response
    }

    suspend fun apiCreateTransaction(request: TransactionCreateRequest): TransactionCreateResponse {
        val response = PayTrackApiClient.apiService.createTransaction(getAuthHeader(), request)
        if (response.success) {
            syncTransactions()
            syncBudgets()
        }
        return response
    }

    suspend fun apiCreateBill(request: BillCreateRequest): BillCreateResponse {
        val response = PayTrackApiClient.apiService.createBill(getAuthHeader(), request)
        if (response.success && response.data != null) {
            val remoteBill = response.data
            val b = Bill(
                id = remoteBill._id.hashCode() and 0x7FFFFFFF,
                userId = 1,
                podId = remoteBill.status.hashCode() and 0x7FFFFFFF,
                name = remoteBill.name,
                amount = remoteBill.amount,
                dueDate = try {
                    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
                    sdf.parse(remoteBill.dueDate)?.time ?: System.currentTimeMillis()
                } catch (e: Exception) {
                    System.currentTimeMillis()
                },
                recurrence = remoteBill.recurrence,
                category = remoteBill.category,
                isPaid = remoteBill.isPaid
            )
            billDao.insertBill(b)
        }
        return response
    }

    suspend fun apiPayBill(billId: String): BillPayResponse {
        val response = PayTrackApiClient.apiService.payBill(getAuthHeader(), billId)
        if (response.success) {
            syncTransactions()
        }
        return response
    }

    suspend fun apiCreateBudget(request: BudgetCreateRequest): BudgetCreateResponse {
        val response = PayTrackApiClient.apiService.createBudget(getAuthHeader(), request)
        if (response.success) {
            syncBudgets()
        }
        return response
    }

    suspend fun apiDeleteAccount(): MessageResponse {
        val response = PayTrackApiClient.apiService.deleteAccount(getAuthHeader())
        if (response.success) {
            clearAllData()
            sessionManager.logout()
        }
        return response
    }
}
