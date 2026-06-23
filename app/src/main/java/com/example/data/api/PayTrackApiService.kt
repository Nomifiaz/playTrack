package com.example.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

// --- REQUEST MODELS ---

@JsonClass(generateAdapter = true)
data class RegisterRequest(
    val name: String,
    val email: String,
    val password: String,
    val currency: String,
    val primaryUseCase: String
)

@JsonClass(generateAdapter = true)
data class LoginRequest(
    val email: String,
    val password: String
)

// Custom Moshi-compatible register wrapper
@JsonClass(generateAdapter = true)
data class LoginBody(
    val email: String,
    val password: String
)

@JsonClass(generateAdapter = true)
data class GoogleAuthRequest(
    val idToken: String
)

@JsonClass(generateAdapter = true)
data class ForgotRequest(
    val email: String
)

@JsonClass(generateAdapter = true)
data class ResetRequest(
    val password: String
)

@JsonClass(generateAdapter = true)
data class ProfileUpdateRequest(
    val name: String?,
    val currency: String?,
    val monthlyIncome: Double?,
    val primaryUseCase: String?,
    val fcmToken: String?
)

@JsonClass(generateAdapter = true)
data class PodCreateRequest(
    val name: String,
    val budgetAmount: Double
)

@JsonClass(generateAdapter = true)
data class PodJoinRequest(
    val inviteCode: String
)

@JsonClass(generateAdapter = true)
data class PodSettleRequest(
    val toUserId: String,
    val amount: Double,
    val paymentMethod: String
)

@JsonClass(generateAdapter = true)
data class TransactionSplitRequest(
    val user: String,
    val amount: Double
)

@JsonClass(generateAdapter = true)
data class TransactionCreateRequest(
    val type: String,
    val amount: Double,
    val category: String,
    val paymentMethod: String,
    val note: String,
    val pod: String?,
    val splitMethod: String,
    val splits: List<TransactionSplitRequest>? = null
)

@JsonClass(generateAdapter = true)
data class BillCreateRequest(
    val name: String,
    val amount: Double,
    val dueDate: String,
    val recurrence: String,
    val category: String,
    val reminderDaysBefore: Int,
    val pod: String?
)

@JsonClass(generateAdapter = true)
data class BudgetCreateRequest(
    val limitAmount: Double,
    val category: String,
    val lowBalanceThresholdPercent: Int,
    val startDate: String,
    val endDate: String
)


// --- RESPONSE MODELS ---

@JsonClass(generateAdapter = true)
data class AuthResponse(
    val success: Boolean,
    val _id: String? = null,
    val name: String? = null,
    val email: String? = null,
    val currency: String? = null,
    val token: String? = null,
    val message: String? = null
)

@JsonClass(generateAdapter = true)
data class ForgotResponse(
    val success: Boolean,
    val message: String? = null,
    val resetLink: String? = null
)

@JsonClass(generateAdapter = true)
data class ResetResponse(
    val success: Boolean,
    val message: String? = null
)

@JsonClass(generateAdapter = true)
data class MessageResponse(
    val success: Boolean,
    val message: String? = null
)

@JsonClass(generateAdapter = true)
data class UserProfileData(
    val _id: String,
    val name: String,
    val email: String,
    val currency: String,
    val photoUrl: String? = null,
    val monthlyIncome: Double? = 0.0,
    val primaryUseCase: String? = null,
    val fcmToken: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class ProfileResponse(
    val success: Boolean,
    val data: UserProfileData? = null
)

@JsonClass(generateAdapter = true)
data class NotificationData(
    val _id: String,
    val user: String? = null,
    val type: String,
    val title: String,
    val message: String,
    val isRead: Boolean,
    val createdAt: String
)

@JsonClass(generateAdapter = true)
data class NotificationsListResponse(
    val success: Boolean,
    val count: Int? = 0,
    val data: List<NotificationData>? = emptyList()
)

@JsonClass(generateAdapter = true)
data class PodData(
    val _id: String,
    val name: String,
    val owner: String,
    val members: List<String>,
    val inviteCode: String,
    val budgetAmount: Double,
    val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class PodCreateResponse(
    val success: Boolean,
    val data: PodData? = null
)

@JsonClass(generateAdapter = true)
data class PodJoinData(
    val _id: String,
    val name: String,
    val members: List<String>
)

@JsonClass(generateAdapter = true)
data class PodJoinResponse(
    val success: Boolean,
    val message: String? = null,
    val data: PodJoinData? = null
)

@JsonClass(generateAdapter = true)
data class BalanceUser(
    val _id: String,
    val name: String,
    val email: String
)

@JsonClass(generateAdapter = true)
data class PodBalance(
    val from: BalanceUser,
    val to: BalanceUser,
    val amount: Double
)

@JsonClass(generateAdapter = true)
data class PodBalancesResponse(
    val success: Boolean,
    val balances: List<PodBalance>? = emptyList()
)

@JsonClass(generateAdapter = true)
data class SettleUser(
    val _id: String,
    val name: String
)

@JsonClass(generateAdapter = true)
data class SettleData(
    val pod: String,
    val fromUser: SettleUser,
    val toUser: SettleUser,
    val amount: Double,
    val paymentMethod: String,
    val status: String,
    val settledAt: String
)

@JsonClass(generateAdapter = true)
data class PodSettleResponse(
    val success: Boolean,
    val message: String? = null,
    val data: SettleData? = null
)

@JsonClass(generateAdapter = true)
data class ActivityItemDetails(
    val id: String? = null,
    val amount: Double? = 0.0,
    val category: String? = null,
    val note: String? = null,
    val paymentMethod: String? = null
)

@JsonClass(generateAdapter = true)
data class ActivityLogData(
    val type: String,
    val timestamp: String,
    val message: String,
    val details: ActivityItemDetails? = null
)

@JsonClass(generateAdapter = true)
data class PodActivityResponse(
    val success: Boolean,
    val count: Int? = 0,
    val data: List<ActivityLogData>? = emptyList()
)

@JsonClass(generateAdapter = true)
data class TransactionSplitData(
    val user: String,
    val amount: Double,
    val percent: Double? = null
)

@JsonClass(generateAdapter = true)
data class TransactionData(
    val _id: String,
    val user: String? = null,
    val pod: String? = null,
    val type: String,
    val amount: Double,
    val category: String,
    val paymentMethod: String? = null,
    val note: String? = null,
    val splitMethod: String? = null,
    val splits: List<TransactionSplitData>? = null,
    val date: String
)

@JsonClass(generateAdapter = true)
data class TransactionCreateResponse(
    val success: Boolean,
    val data: TransactionData? = null
)

@JsonClass(generateAdapter = true)
data class FeedTotals(
    val income: Double,
    val expense: Double,
    val net: Double
)

@JsonClass(generateAdapter = true)
data class FeedUser(
    val _id: String,
    val name: String
)

@JsonClass(generateAdapter = true)
data class FeedPod(
    val _id: String,
    val name: String
)

@JsonClass(generateAdapter = true)
data class FeedTransaction(
    val _id: String,
    val user: FeedUser?,
    val type: String,
    val amount: Double,
    val category: String,
    val date: String,
    val note: String? = null,
    val pod: FeedPod? = null
)

@JsonClass(generateAdapter = true)
data class TransactionsFeedResponse(
    val success: Boolean,
    val count: Int? = 0,
    val totals: FeedTotals? = null,
    val data: List<FeedTransaction>? = emptyList()
)

@JsonClass(generateAdapter = true)
data class ReceiptUploadResponse(
    val success: Boolean,
    val filename: String? = null,
    val receiptUrl: String? = null,
    val message: String? = null
)

@JsonClass(generateAdapter = true)
data class BillData(
    val _id: String,
    val name: String,
    val amount: Double,
    val dueDate: String,
    val recurrence: String,
    val category: String,
    val status: String,
    val reminderDaysBefore: Int,
    val isPaid: Boolean
)

@JsonClass(generateAdapter = true)
data class BillCreateResponse(
    val success: Boolean,
    val data: BillData? = null
)

@JsonClass(generateAdapter = true)
data class BillPayData(
    val bill: BillData,
    val transaction: TransactionData? = null
)

@JsonClass(generateAdapter = true)
data class BillPayResponse(
    val success: Boolean,
    val message: String? = null,
    val data: BillPayData? = null
)

@JsonClass(generateAdapter = true)
data class BudgetData(
    val _id: String,
    val user: String,
    val limitAmount: Double,
    val category: String,
    val lowBalanceThresholdPercent: Int,
    val startDate: String,
    val endDate: String
)

@JsonClass(generateAdapter = true)
data class BudgetCreateResponse(
    val success: Boolean,
    val data: BudgetData? = null
)

@JsonClass(generateAdapter = true)
data class BudgetProgressData(
    val budgetId: String,
    val category: String,
    val limitAmount: Double,
    val spentAmount: Double,
    val remainingAmount: Double,
    val percentSpent: Double,
    val startDate: String,
    val endDate: String,
    val lowBalanceThresholdPercent: Int,
    val isOverspent: Boolean
)

@JsonClass(generateAdapter = true)
data class BudgetProgressResponse(
    val success: Boolean,
    val count: Int? = 0,
    val data: List<BudgetProgressData>? = emptyList()
)

@JsonClass(generateAdapter = true)
data class ReportSummaryBreakdown(
    @Json(name = "Food & Dining") val foodDining: Double? = 0.0,
    @Json(name = "Utilities & Bills") val utilitiesBills: Double? = 0.0
)

@JsonClass(generateAdapter = true)
data class BudgetAdherenceItem(
    val category: String,
    val limit: Double,
    val spent: Double,
    val adhered: Boolean
)

@JsonClass(generateAdapter = true)
data class ReportSummaryData(
    val title: String,
    val userName: String,
    val currency: String,
    val periodString: String,
    val totalIncome: Double,
    val totalExpense: Double,
    val categoriesBreakdown: Map<String, Double>? = emptyMap(),
    val budgetsAdherence: List<BudgetAdherenceItem>? = emptyList()
)

@JsonClass(generateAdapter = true)
data class ReportSummaryResponse(
    val success: Boolean,
    val data: ReportSummaryData? = null
)


// --- RETROFIT INTERFACE ---

interface PayTrackApiService {

    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): AuthResponse

    @POST("auth/login")
    suspend fun login(@Body body: LoginBody): AuthResponse

    @POST("auth/google")
    suspend fun googleSignIn(@Body request: GoogleAuthRequest): AuthResponse

    @POST("auth/forgot-password")
    suspend fun forgotPassword(@Body request: ForgotRequest): ForgotResponse

    @POST("auth/reset-password/{token}")
    suspend fun resetPassword(@Path("token") token: String, @Body request: ResetRequest): ResetResponse

    @DELETE("auth/delete-account")
    suspend fun deleteAccount(@Header("Authorization") token: String): MessageResponse

    @GET("users/profile")
    suspend fun getUserProfile(@Header("Authorization") token: String): ProfileResponse

    @PUT("users/profile")
    suspend fun updateProfile(
        @Header("Authorization") token: String,
        @Body request: ProfileUpdateRequest
    ): ProfileResponse

    @GET("users/notifications")
    suspend fun getNotifications(@Header("Authorization") token: String): NotificationsListResponse

    @POST("pods")
    suspend fun createPod(
        @Header("Authorization") token: String,
        @Body request: PodCreateRequest
    ): PodCreateResponse

    @POST("pods/join")
    suspend fun joinPod(
        @Header("Authorization") token: String,
        @Body request: PodJoinRequest
    ): PodJoinResponse

    @GET("pods/{id}/balances")
    suspend fun getPodBalances(
        @Header("Authorization") token: String,
        @Path("id") podId: String
    ): PodBalancesResponse

    @POST("pods/{id}/settle")
    suspend fun settlePodDebt(
        @Header("Authorization") token: String,
        @Path("id") podId: String,
        @Body request: PodSettleRequest
    ): PodSettleResponse

    @GET("pods/{id}/activity")
    suspend fun getPodActivity(
        @Header("Authorization") token: String,
        @Path("id") podId: String
    ): PodActivityResponse

    @POST("transactions")
    suspend fun createTransaction(
        @Header("Authorization") token: String,
        @Body request: TransactionCreateRequest
    ): TransactionCreateResponse

    @GET("transactions")
    suspend fun getTransactions(
        @Header("Authorization") token: String,
        @Query("startDate") startDate: String?,
        @Query("endDate") endDate: String?,
        @Query("category") category: String?,
        @Query("type") type: String?
    ): TransactionsFeedResponse

    @Multipart
    @POST("transactions/upload")
    suspend fun uploadReceipt(
        @Header("Authorization") token: String,
        @Part receipt: MultipartBody.Part
    ): ReceiptUploadResponse

    @POST("bills")
    suspend fun createBill(
        @Header("Authorization") token: String,
        @Body request: BillCreateRequest
    ): BillCreateResponse

    @POST("bills/{id}/pay")
    suspend fun payBill(
        @Header("Authorization") token: String,
        @Path("id") billId: String
    ): BillPayResponse

    @POST("budgets")
    suspend fun createBudget(
        @Header("Authorization") token: String,
        @Body request: BudgetCreateRequest
    ): BudgetCreateResponse

    @GET("budgets/progress")
    suspend fun getBudgetsProgress(@Header("Authorization") token: String): BudgetProgressResponse

    @GET("reports/summary")
    suspend fun getReportSummary(
        @Header("Authorization") token: String,
        @Query("startDate") startDate: String?,
        @Query("endDate") endDate: String?,
        @Query("period") period: String?
    ): ReportSummaryResponse
}

// --- NETWORK CLIENT INJECTOR ---

object PayTrackApiClient {
    private const val BASE_URL = "https://builder.warriach.online/api/"

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(logging)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val apiService: PayTrackApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(PayTrackApiService::class.java)
    }
}
