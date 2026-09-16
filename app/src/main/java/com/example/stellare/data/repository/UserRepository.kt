package com.example.stellare.data.repository

import com.example.stellare.data.remote.AuthDataSource
import com.example.stellare.data.remote.UserDataSource
import com.example.stellare.data.remote.FavoriteDataSource
import com.example.stellare.data.model.UserModel
import com.example.stellare.data.model.FavoriteModel
import com.example.stellare.data.model.UserRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

class UserRepository(
    private val authDataSource: AuthDataSource,
    private val userDataSource: UserDataSource,
    private val favoriteDataSource: FavoriteDataSource
) {
    // Users
    fun getCurrentUserFlow(): Flow<UserModel?> = authDataSource.currentUserId
        .flatMapLatest { uid ->
            if (uid != null) userDataSource.getUser(uid)
            else flowOf(null)
        }

    suspend fun getCurrentUser(): UserModel? =
        authDataSource.getCurrentUserIdOnce()?.let { userDataSource.getUserOnce(it) }

    fun getUsersFlow(role: String, currentUserId: String): Flow<List<UserModel>> =
        userDataSource.getAllByRoleExcluding(role, currentUserId)

    fun getUsersOfRoleFlow(role: UserRole): Flow<List<UserModel>> =
        userDataSource.getAllByRole(role.name)

    suspend fun getUserById(userId: String): UserModel? =
        userDataSource.getUserOnce(userId)

    fun getUserByIdFlow(userId: String): Flow<UserModel?> =
        userDataSource.getUser(userId)

    suspend fun insertUser(user: UserModel) =
        userDataSource.saveUser(user)

    suspend fun updateUser(user: UserModel) =
        userDataSource.saveUser(user)

    // Favorites
    fun getFavoritesForUserFlow(userId: String) =
        favoriteDataSource.getFavoritesForUser(userId)

    suspend fun insertFavorite(userId: String, targetId: String) =
        favoriteDataSource.saveFavorite(FavoriteModel(userId, targetId))

    suspend fun deleteFavorite(userId: String, targetId: String) =
        favoriteDataSource.deleteFavorite(userId, targetId)
}