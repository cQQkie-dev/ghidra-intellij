package com.codingmates.ghidra.intellij.ide.scripteditor

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.LocalFileSystem
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Path
import kotlin.concurrent.thread

private const val DEFAULT_SCRIPT_EDITOR_PORT = 12321
private const val LOOPBACK_ADDRESS = "127.0.0.1"

/**
 * Listens for Ghidra Script Manager external-editor requests.
 *
 * The listener is disabled by default and binds only to IPv4 loopback. It implements the
 * Eclipse GhidraDev-compatible request format:
 *
 * open_/absolute/path/to/script.java
 */
@Service(Service.Level.APP)
@State(
    name = "GhidraScriptEditorListener",
    storages = [Storage("ghidra-script-editor.xml")],
)
class GhidraScriptEditorListenerService :
    PersistentStateComponent<GhidraScriptEditorListenerState>,
    Disposable {

    companion object {
        private val logger =
            Logger.getInstance(GhidraScriptEditorListenerService::class.java)
    }

    private val lock = Any()

    private var state = GhidraScriptEditorListenerState()
    private var serverSocket: ServerSocket? = null

    override fun getState(): GhidraScriptEditorListenerState = state

    override fun loadState(state: GhidraScriptEditorListenerState) {
        synchronized(lock) {
            stopListenerLocked()
            this.state = state

            runCatching {
                if (state.enabled) {
                    validatePort(state.port)
                    startListenerLocked(state.port)
                }
            }
        }
    }

    fun settings(): GhidraScriptEditorListenerState =
        synchronized(lock) {
            GhidraScriptEditorListenerState().also {
                it.enabled = state.enabled
                it.port = state.port
            }
        }

    @Throws(IOException::class, IllegalArgumentException::class)
    fun updateSettings(
        enabled: Boolean,
        port: Int,
    ) {
        validatePort(port)

        synchronized(lock) {
            val previousState = settings()
            stopListenerLocked()

            try {
                if (enabled) {
                    startListenerLocked(port)
                }

                state.enabled = enabled
                state.port = port
            }
            catch (e: IOException) {
                state = previousState

                runCatching {
                    if (previousState.enabled) {
                        startListenerLocked(previousState.port)
                    }
                }

                throw e
            }
        }
    }

    /**
     * Starts the persisted listener configuration when it is enabled and not already running.
     */
    fun startIfEnabled() {
        synchronized(lock) {
            if (!state.enabled || serverSocket != null) {
                return
            }

            runCatching {
                validatePort(state.port)
                startListenerLocked(state.port)
            }.onFailure { error ->
                logger.warn(
                    "Failed to start Ghidra Script Editor listener on port ${state.port}.",
                    error,
                )
            }
        }
    }

    override fun dispose() {
        synchronized(lock) {
            stopListenerLocked()
        }
    }

    private fun startListenerLocked(port: Int) {
        val socket = ServerSocket()
        socket.reuseAddress = true
        socket.bind(
            InetSocketAddress(
                InetAddress.getByName(LOOPBACK_ADDRESS),
                port,
            )
        )

        serverSocket = socket

        thread(
            start = true,
            isDaemon = true,
            name = "Ghidra Script Editor Listener",
        ) {
            acceptConnections(socket)
        }
    }

    private fun stopListenerLocked() {
        serverSocket?.runCatching(ServerSocket::close)
        serverSocket = null
    }

    private fun acceptConnections(socket: ServerSocket) {
        while (!socket.isClosed) {
            val client = try {
                socket.accept()
            }
            catch (_: IOException) {
                return
            }

            handleClient(client)
        }
    }

    private fun handleClient(socket: Socket) {
        socket.use { client ->
            if (!client.inetAddress.isLoopbackAddress) {
                return
            }

            client.soTimeout = 5_000

            BufferedReader(
                InputStreamReader(client.getInputStream()),
            ).use { input ->
                while (true) {
                    val line = input.readLine() ?: return

                    val requestedPath =
                        GhidraScriptEditorProtocol.requestedPath(line)
                            ?: continue

                    if (Files.isRegularFile(requestedPath)) {
                        openFileInEditor(requestedPath)
                    }
                }
            }
        }
    }

    private fun openFileInEditor(path: Path) {
        ApplicationManager.getApplication().invokeLater(
            {
                val project = projectFor(path) ?: return@invokeLater

                val virtualFile = LocalFileSystem.getInstance()
                    .refreshAndFindFileByNioFile(path)
                    ?: return@invokeLater

                FileEditorManager.getInstance(project)
                    .openFile(virtualFile, true)
            },
            ModalityState.nonModal(),
        )
    }

    private fun projectFor(path: Path): Project? {
        val openProjects = ProjectManager.getInstance()
            .openProjects
            .filterNot(Project::isDisposed)

        return openProjects.firstOrNull { project ->
            project.basePath
                ?.let(Path::of)
                ?.toAbsolutePath()
                ?.normalize()
                ?.let(path::startsWith)
                ?: false
        } ?: openProjects.firstOrNull()
    }

    private fun validatePort(port: Int) {
        require(port in 1024..65535) {
            "Script Editor port must be between 1024 and 65535."
        }
    }
}

class GhidraScriptEditorListenerState {
    var enabled: Boolean = false
    var port: Int = DEFAULT_SCRIPT_EDITOR_PORT
}