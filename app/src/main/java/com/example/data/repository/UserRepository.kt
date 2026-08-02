package com.example.data.repository

import com.example.data.local.dao.FavoriteDao
import com.example.data.local.dao.UserDao
import com.example.data.local.entity.FavoriteEntity
import com.example.data.local.entity.UserEntity
import kotlinx.coroutines.flow.Flow

class UserRepository(private val userDao: UserDao, private val favoriteDao: FavoriteDao) {
    fun getCurrentUserFlow(): Flow<UserEntity?> = userDao.getCurrentUserFlow()
    suspend fun getCurrentUser(): UserEntity? = userDao.getCurrentUser()
    fun getUsersFlow(role: String, currentUserId: Int): Flow<List<UserEntity>> = userDao.getUsersFlow(role, currentUserId)
    fun getUsersOfRoleFlow(role: String): Flow<List<UserEntity>> = userDao.getUsersOfRoleFlow(role)
    suspend fun getUserById(userId: Int): UserEntity? = userDao.getUserById(userId)
    fun getUserByIdFlow(userId: Int): Flow<UserEntity?> = userDao.getUserByIdFlow(userId)
    suspend fun insertUser(user: UserEntity): Long = userDao.insertUser(user)
    suspend fun updateUser(user: UserEntity) = userDao.updateUser(user)
    suspend fun clearCurrentUser() = userDao.clearCurrentUser()

    // Favorites
    fun getFavoritesForUserFlow(userId: Int): Flow<List<FavoriteEntity>> = favoriteDao.getFavoritesForUserFlow(userId)
    suspend fun insertFavorite(userId: Int, psychologistId: Int) = favoriteDao.insertFavorite(FavoriteEntity(userId, psychologistId))
    suspend fun deleteFavorite(userId: Int, psychologistId: Int) = favoriteDao.deleteFavorite(userId, psychologistId)
}
