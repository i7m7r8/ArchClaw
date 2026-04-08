package io.archclaw.setup

import androidx.lifecycle.ViewModel
import io.archclaw.ArchClawApp
import io.archclaw.core.SetupStep
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class SetupViewModel : ViewModel() {

    fun setupProgress(): Flow<SetupStep> = flow {
        val prootManager = ArchClawApp.instance.prootManager
        prootManager.setupProgress().collect { step ->
            emit(step)
        }
    }
}
