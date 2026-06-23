package com.example.data.repository

import android.util.Log
import com.example.data.dao.*
import com.example.data.model.*
import com.example.data.api.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import okhttp3.MultipartBody
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
            Log.d("AppRepository", "Seeding database with primary user placeholder only...")

            // 1. Create Default Users (Only Primary User, no dummy transactional data)
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
                    val localPodId = feedTx.pod?._id?.hashCode()?.let { it and 0x7FFFFFFF }
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
                        thresholdPercent = budgetData.lowBalanceThresholdPercent,
                        spentAmount = budgetData.spentAmount,
                        remainingAmount = budgetData.remainingAmount,
                        percentSpent = budgetData.percentSpent,
                        isOverspent = budgetData.isOverspent
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

    suspend fun apiFetchNotifications(): NotificationsListResponse {
        val token = sessionManager.token ?: return NotificationsListResponse(success = false, count = 0, data = emptyList())
        return PayTrackApiClient.apiService.getNotifications(getAuthHeader())
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

    suspend fun apiGetPodBalances(pod: Pod): PodBalancesResponse {
        val remoteId = getRemotePodId(pod) ?: return PodBalancesResponse(success = false, balances = emptyList())
        return PayTrackApiClient.apiService.getPodBalances(getAuthHeader(), remoteId)
    }

    suspend fun apiSettlePodDebt(pod: Pod, request: PodSettleRequest): PodSettleResponse {
        val remoteId = getRemotePodId(pod) ?: return PodSettleResponse(success = false, message = "Local-only pod")
        return PayTrackApiClient.apiService.settlePodDebt(getAuthHeader(), remoteId, request)
    }

    suspend fun apiGetPodActivity(pod: Pod): PodActivityResponse {
        val remoteId = getRemotePodId(pod) ?: return PodActivityResponse(success = false, count = 0, data = emptyList())
        return PayTrackApiClient.apiService.getPodActivity(getAuthHeader(), remoteId)
    }

    suspend fun apiCreateTransaction(request: TransactionCreateRequest): TransactionCreateResponse {
        val response = PayTrackApiClient.apiService.createTransaction(getAuthHeader(), request)
        if (response.success) {
            syncTransactions()
            syncBudgets()
        }
        return response
    }

    suspend fun apiUploadReceipt(receipt: MultipartBody.Part): ReceiptUploadResponse {
        return PayTrackApiClient.apiService.uploadReceipt(getAuthHeader(), receipt)
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
                isPaid = remoteBill.isPaid,
                remoteId = remoteBill._id
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
