package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profiles")
data class UserProfile(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val email: String,
    val currencyCode: String = "USD",
    val currencySymbol: String = "$",
    val avatarName: String = "Richie",
    val monthlyIncome: Double = 0.0,
    val isPrimary: Boolean = false
)

@Entity(tableName = "pods")
data class Pod(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val description: String,
    val creatorId: Int = 1,
    val inviteCode: String = "",
    val budgetAmount: Double = 0.0,
    val currencyCode: String = "USD"
)

@Entity(tableName = "pod_members")
data class PodMember(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val podId: Int,
    val userId: Int,
    val userName: String,
    val avatarName: String = "member_avatar"
)

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: Int = 1,
    val podId: Int? = null, // null means personal expense/income
    val type: String, // "EXPENSE" or "INCOME"
    val amount: Double,
    val category: String,
    val date: Long = System.currentTimeMillis(),
    val paymentMethod: String = "Card",
    val note: String = "",
    val paidByUserId: Int = 1, // Who originally paid
    val splitMethod: String = "EQUAL", // "EQUAL", "PERCENTAGE", "EXACT", "PAID_BY_ONE_ALL_OWE"
    val splitDetails: String = "", // e.g. "1:33.3, 2:33.3, 3:33.3" (userId:amount or share)
    val isRecurring: Boolean = false,
    val receiptPhotoPath: String? = null
)

@Entity(tableName = "bills")
data class Bill(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: Int = 1,
    val podId: Int? = null, // assigned to a Pod if shared (e.g. rent)
    val name: String,
    val amount: Double,
    val dueDate: Long,
    val recurrence: String = "Monthly", // "One-time", "Weekly", "Monthly", "Custom"
    val category: String = "Bills",
    val isPaid: Boolean = false,
    val paidAt: Long? = null,
    val remoteId: String? = null
)

@Entity(tableName = "budgets")
data class Budget(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: Int = 1,
    val podId: Int? = null, // null for personal category budgets
    val category: String = "Overall", // "Overall" or category name
    val limitAmount: Double,
    val thresholdPercent: Int = 80, // alert when remaining budget drops below (100 - thresholdPercent)%
    val spentAmount: Double = 0.0,
    val remainingAmount: Double = 0.0,
    val percentSpent: Double = 0.0,
    val isOverspent: Boolean = false
)
