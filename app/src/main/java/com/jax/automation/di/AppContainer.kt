package com.jax.automation.di

import android.app.Application
import com.jax.automation.accessibility.AccessibilityAutomationAdapter
import com.jax.automation.ai.CommandInterpreter
import com.jax.automation.ai.PromptBuilder
import com.jax.automation.ai.ProviderRegistry
import com.jax.automation.ai.TaskPlanner
import com.jax.automation.automation.AutomationAdapter
import com.jax.automation.browser.ChromeAdapter
import com.jax.automation.database.JaxDatabase
import com.jax.automation.database.ProjectDao
import com.jax.automation.database.SceneDao
import com.jax.automation.downloads.DownloadManager
import com.jax.automation.files.FileImporter
import com.jax.automation.flow.GoogleFlowAdapter
import com.jax.automation.logging.JaxLogger
import com.jax.automation.logging.JaxLoggerImpl
import com.jax.automation.qa.VisionQA
import com.jax.automation.references.LockManager
import com.jax.automation.references.LockManagerImpl
import com.jax.automation.references.ReferenceRepository
import com.jax.automation.references.ReferenceRepositoryImpl
import com.jax.automation.security.SecureStorage
import com.jax.automation.security.SecureStorageImpl
import com.jax.automation.settings.SettingsRepository
import com.jax.automation.tasks.RecoveryManager
import com.jax.automation.tasks.TaskExecutor
import com.jax.automation.tasks.TaskQueue
import kotlinx.coroutines.CoroutineScope

/**
 * Manual DI container. Constructed once per process from [JaxApplication].
 * `executor` is lazy because [flow]'s onNeedUser callback references it;
 * the lambda only runs at runtime, after init has completed.
 */
class AppContainer(val app: Application, private val appScope: CoroutineScope) {
    private val db = JaxDatabase.get(app)

    val logger: JaxLogger = JaxLoggerImpl(db.logDao(), appScope)
    val secureStorage: SecureStorage = SecureStorageImpl(app)
    val settings: SettingsRepository = SettingsRepository.create(app)
    val recovery: RecoveryManager = RecoveryManager.create(app)
    val registry = ProviderRegistry(secureStorage, db.aiProviderDao(), settings, logger)
    val interpreter = CommandInterpreter(registry, logger)
    val planner = TaskPlanner(interpreter)
    val lockManager: LockManager = LockManagerImpl(db.lockDao(), logger)
    val references: ReferenceRepository = ReferenceRepositoryImpl(db.referenceDao(), logger)
    val promptBuilder = PromptBuilder(lockManager)
    val queue = TaskQueue(db.taskDao(), logger)
    val projectDao: ProjectDao = db.projectDao()
    val sceneDao: SceneDao = db.sceneDao()
    val automationAdapter: AutomationAdapter = AccessibilityAutomationAdapter(app)
    val chrome = ChromeAdapter(app, automationAdapter, logger)
    val flow = GoogleFlowAdapter(app, automationAdapter, logger, onNeedUser = { msg ->
        executor.enterWaitingUser(msg)
    })
    val qa = VisionQA(registry, settings, db.qaDao(), logger)
    val downloads = DownloadManager(app, db.downloadDao(), logger)
    val fileImporter = FileImporter(app, logger)

    val executor: TaskExecutor by lazy {
        TaskExecutor(
            app, appScope, automationAdapter, queue, chrome, flow, qa,
            downloads, projectDao, sceneDao, recovery, settings, logger
        )
    }
}
