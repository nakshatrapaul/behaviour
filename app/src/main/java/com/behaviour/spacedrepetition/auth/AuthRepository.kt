package com.behaviour.spacedrepetition.auth

import io.appwrite.ID
import io.appwrite.exceptions.AppwriteException
import io.appwrite.models.User
import javax.inject.Inject
import javax.inject.Singleton

sealed class AuthResult {
    data class Success(val userId: String) : AuthResult()
    data class Error(val message: String) : AuthResult()
}

@Singleton
class AuthRepository @Inject constructor() {

    private val account get() = AppwriteClient.account

    suspend fun register(email: String, password: String, name: String = ""): AuthResult {
        return try {
            account.create(
                userId = ID.unique(),
                email = email,
                password = password,
                name = name.ifBlank { null }
            )
            // Auto-login after registration
            login(email, password)
        } catch (e: AppwriteException) {
            AuthResult.Error(e.message ?: "Registration failed")
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "An unexpected error occurred")
        }
    }

    suspend fun login(email: String, password: String): AuthResult {
        return try {
            account.createEmailPasswordSession(
                email = email,
                password = password
            )
            val user = account.get()
            AuthResult.Success(user.id)
        } catch (e: AppwriteException) {
            AuthResult.Error(e.message ?: "Login failed")
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "An unexpected error occurred")
        }
    }

    suspend fun logout(): AuthResult {
        return try {
            account.deleteSession("current")
            AuthResult.Success("")
        } catch (e: AppwriteException) {
            AuthResult.Error(e.message ?: "Logout failed")
        }
    }

    suspend fun getCurrentUser(): User<Map<String, Any>>? {
        return try {
            account.get()
        } catch (e: Exception) {
            null
        }
    }

    suspend fun isLoggedIn(): Boolean {
        return try {
            account.get()
            true
        } catch (e: Exception) {
            false
        }
    }
}
