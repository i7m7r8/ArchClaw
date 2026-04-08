package io.archclaw.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.archclaw.ArchClawApp
import io.archclaw.core.ProotManager
import io.archclaw.core.SetupStep
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class SetupViewModel : ViewModel() {

    fun setupProgress(): Flow<SetupStep> = flow {
        val prootManager = ProotManager(ArchClawApp.instance)
        
        prootManager.setupProgress().collect { step ->
            emit(step)
        }
    }
}
