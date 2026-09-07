package com.example.tuner

import android.app.Application
import com.example.tuner.data.repository.AppStateRepository
import com.example.tuner.data.repository.ChannelRepository
import com.example.tuner.data.repository.CustomSourceRepository
import com.example.tuner.data.repository.FavoritesRepository
import com.example.tuner.data.repository.HistoryRepository

/**
 * Lightweight manual DI container — no Hilt needed for a repository graph this small.
 * Repositories are simple singletons scoped to the process.
 */
class TunerApplication : Application() {

    lateinit var channelRepository: ChannelRepository
        private set
    lateinit var customSourceRepository: CustomSourceRepository
        private set
    lateinit var appStateRepository: AppStateRepository
        private set
    lateinit var favoritesRepository: FavoritesRepository
        private set
    lateinit var historyRepository: HistoryRepository
        private set

    override fun onCreate() {
        super.onCreate()
        channelRepository = ChannelRepository()
        customSourceRepository = CustomSourceRepository(applicationContext)
        appStateRepository = AppStateRepository(applicationContext)
        favoritesRepository = FavoritesRepository(applicationContext)
        historyRepository = HistoryRepository(applicationContext)
    }
}
