package com.raido.videoeditor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.raido.videoeditor.data.ProjectStore
import com.raido.videoeditor.ui.EditorScreen
import com.raido.videoeditor.ui.HomeScreen
import com.raido.videoeditor.ui.RaidoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = ProjectStore(applicationContext)

        setContent {
            RaidoTheme {
                var projects by remember { mutableStateOf(store.list()) }
                var openProjectId by rememberSaveable { mutableStateOf<String?>(null) }

                val id = openProjectId
                if (id == null) {
                    HomeScreen(
                        projects = projects,
                        onCreate = { name, aspect ->
                            val created = store.create(name, aspect)
                            projects = store.list()
                            openProjectId = created.id
                        },
                        onDelete = { project ->
                            store.delete(project.id)
                            projects = store.list()
                        },
                        onOpen = { openProjectId = it.id },
                    )
                } else {
                    EditorScreen(
                        projectId = id,
                        onBack = {
                            projects = store.list()
                            openProjectId = null
                        },
                    )
                }
            }
        }
    }
}
