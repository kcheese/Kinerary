package com.kyle.kinerary

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.kyle.kinerary.data.TripRepository
import com.kyle.kinerary.data.Updater
import kotlinx.coroutines.launch

class KineraryApp : Application() {
    lateinit var repository: TripRepository
        private set
    lateinit var updater: Updater
        private set

    override fun onCreate() {
        super.onCreate()
        repository = TripRepository(this)
        updater = Updater(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                repository.onForeground()
                owner.lifecycleScope.launch { updater.checkQuietly() }
            }

            override fun onStop(owner: LifecycleOwner) = repository.onBackground()
        })
    }
}
