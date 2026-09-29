package com.example

import android.app.Application
import com.example.data.local.AppDatabase
import com.example.data.preference.UserPreferencesRepository
import com.example.data.repository.CredentialRepository
import com.example.data.repository.PasswordBatchRepository
import com.example.data.repository.WiFiConnectionRepository
import com.example.wifi.AuthorizedRouterTestManager
import com.example.wifi.NetworkDeviceScanner
import com.example.wifi.WifiConnector
import com.example.wifi.WifiScannerManager

class WifiManagerApp : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var credentialRepository: CredentialRepository
        private set

    lateinit var preferencesRepository: UserPreferencesRepository
        private set

    lateinit var wifiScannerManager: WifiScannerManager
        private set

    lateinit var wifiConnector: WifiConnector
        private set

    lateinit var wifiConnectionRepository: WiFiConnectionRepository
        private set

    lateinit var networkDeviceScanner: NetworkDeviceScanner
        private set

    lateinit var authorizedRouterTestManager: AuthorizedRouterTestManager
        private set

    lateinit var passwordBatchRepository: PasswordBatchRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = AppDatabase.getInstance(this)
        credentialRepository = CredentialRepository(database.credentialDao())
        preferencesRepository = UserPreferencesRepository(this)
        passwordBatchRepository = PasswordBatchRepository(this)
        wifiScannerManager = WifiScannerManager(this)
        wifiConnector = WifiConnector(this)
        wifiConnectionRepository = WiFiConnectionRepository(wifiConnector)
        networkDeviceScanner = NetworkDeviceScanner(this)
        authorizedRouterTestManager = AuthorizedRouterTestManager(this, wifiConnector, wifiConnectionRepository)
    }

    companion object {
        lateinit var instance: WifiManagerApp
            private set
    }
}
