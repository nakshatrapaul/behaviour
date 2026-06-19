package com.behaviour.spacedrepetition.auth

import android.content.Context
import io.appwrite.Client
import io.appwrite.services.Account
import io.appwrite.services.Databases

/**
 * Singleton Appwrite client.
 */
object AppwriteClient {

    private const val ENDPOINT = "https://fra.cloud.appwrite.io/v1"
    private const val PROJECT_ID = "6a33a33e002fd20db8eb"

    private lateinit var client: Client
    lateinit var account: Account
        private set
    lateinit var databases: Databases
        private set

    var isInitialized = false
        private set

    fun init(context: Context) {
        if (isInitialized) return

        client = Client(context.applicationContext)
            .setEndpoint(ENDPOINT)
            .setProject(PROJECT_ID)
            .setSelfSigned(false)

        account = Account(client)
        databases = Databases(client)
        isInitialized = true
    }
}
