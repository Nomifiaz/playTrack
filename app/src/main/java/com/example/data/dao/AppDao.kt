package com.example.data.dao

import androidx.room.*
import com.example.data.model.*
import com.example.data.model.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profiles WHERE isPrimary = 1 LIMIT 1")
    fun getPrimaryUser(): Flow<UserProfile?>

    @Query("SELECT * FROM user_profiles WHERE id = :id")
    suspend fun getUserById(id: Int): UserProfile?

    @Query("SELECT * FROM user_profiles")
    fun getAllUsers(): Flow<List<UserProfile>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserProfile): Long

    @Update
    suspend fun updateUser(user: UserProfile)

    @Delete
    suspend fun deleteUser(user: UserProfile)
    
    @Query("DELETE FROM user_profiles")
    suspend fun clearAllUsers()
}

@Dao
interface PodDao {
    @Query("SELECT * FROM pods ORDER BY id DESC")
    fun getAllPods(): Flow<List<Pod>>

    @Query("SELECT * FROM pods WHERE id = :podId")
    fun getPodById(podId: Int): Flow<Pod?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPod(pod: Pod): Long

    @Delete
    suspend fun deletePod(pod: Pod)

    @Query("SELECT * FROM pod_members WHERE podId = :podId")
    fun getPodMembers(podId: Int): Flow<List<PodMember>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPodMember(member: PodMember): Long

    @Delete
    suspend fun deletePodMember(member: PodMember)

    @Query("DELETE FROM pod_members WHERE podId = :podId")
    suspend fun deleteMembersByPodId(podId: Int)
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY date DESC")
    fun getAllTransactions(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE podId = :podId ORDER BY date DESC")
    fun getTransactionsByPod(podId: Int): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE podId IS NULL ORDER BY date DESC")
    fun getPersonalTransactions(): Flow<List<Transaction>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(tx: Transaction): Long

    @Delete
    suspend fun deleteTransaction(tx: Transaction)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransactionById(id: Int)
    
    @Query("DELETE FROM transactions")
    suspend fun clearAllTransactions()
}

@Dao
interface BillDao {
    @Query("SELECT * FROM bills ORDER BY dueDate ASC")
    fun getAllBills(): Flow<List<Bill>>

    @Query("SELECT * FROM bills WHERE isPaid = 0 ORDER BY dueDate ASC")
    fun getUpcomingBills(): Flow<List<Bill>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBill(bill: Bill): Long

    @Update
    suspend fun updateBill(bill: Bill)

    @Delete
    suspend fun deleteBill(bill: Bill)
    
    @Query("DELETE FROM bills")
    suspend fun clearAllBills()
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets")
    fun getAllBudgets(): Flow<List<Budget>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBudget(budget: Budget): Long

    @Update
    suspend fun updateBudget(budget: Budget)

    @Delete
    suspend fun deleteBudget(budget: Budget)
    
    @Query("DELETE FROM budgets")
    suspend fun clearAllBudgets()
}
