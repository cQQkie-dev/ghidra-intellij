package com.codingmates.ghidra.intellij.ide.facet

import com.codingmates.ghidra.intellij.ide.GhidraBundle
import com.codingmates.ghidra.intellij.ide.installation.GhidraInstallationRegistryService
import com.codingmates.ghidra.intellij.ide.facet.model.isGhidraInstallationPath
import com.codingmates.ghidra.intellij.ide.facet.model.isGhidraSourcesPath
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.facet.ui.FacetEditorContext
import com.intellij.facet.ui.FacetEditorTab
import com.intellij.facet.ui.FacetEditorValidator
import com.intellij.facet.ui.FacetValidatorsManager
import com.intellij.facet.ui.SlowFacetEditorValidator
import com.intellij.facet.ui.ValidationResult
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import javax.swing.JComboBox
import org.jetbrains.annotations.Nls

class GhidraFacetConfigurationEditor(
    private val state: GhidraFacetSettings,
    private val context: FacetEditorContext,
    private val validator: FacetValidatorsManager
) : FacetEditorTab() {

    private val propertyGraph = PropertyGraph()
    private val installationDir = propertyGraph.property(state.installationPath)
    private val settingsDir = propertyGraph.property(state.settingsPath.orEmpty())
    private val version = propertyGraph.property(state.version.orEmpty())
    private val applied = propertyGraph.property(false)

    private val installationRegistry: GhidraInstallationRegistryService
        get() = ApplicationManager.getApplication()
            .getService(GhidraInstallationRegistryService::class.java)

    init {
        validator.registerValidator(GhidraInstallationValidator())
        propertyGraph.afterPropagation { validator.validate() }
    }

    override fun createComponent() = panel {
        group("Ghidra Settings") {
            row(GhidraBundle.message("ghidra.facet.editor.installation")) {
                val installations = registeredInstallationPaths()
                val comboBox = JComboBox(installations.toTypedArray())

                comboBox.selectedItem = installationDir.get()
                comboBox.addActionListener {
                    (comboBox.selectedItem as? String)?.let(installationDir::set)
                }

                cell(comboBox)
                    .align(AlignX.FILL)

                button("Manage...") {
                    ShowSettingsUtil.getInstance().showSettingsDialog(
                        context.project,
                        "Ghidra Installations",
                    )

                    refreshInstallationComboBox(comboBox)
                }
            }
        }

        group("Ghidra Installation Details") {
            row("Version") {
                textField()
                    .bindText(version)
                    .enabled(false)
                    .align(AlignX.FILL)
            }

            row("Settings") {
                textField()
                    .bindText(settingsDir)
                    .enabled(false)
                    .align(AlignX.FILL)

            }
        }.visibleIf(applied)
    }

    private fun registeredInstallationPaths(): List<String> {
        val paths = installationRegistry.configuredPaths()
            .map(Path::toString)
            .toMutableList()

        val currentPath = installationDir.get()
        if (currentPath.isNotBlank() && currentPath !in paths) {
            paths.add(0, currentPath)
        }

        return paths
    }

    private fun refreshInstallationComboBox(comboBox: JComboBox<String>) {
        val selectedPath = installationDir.get()
        comboBox.removeAllItems()

        registeredInstallationPaths().forEach(comboBox::addItem)

        comboBox.selectedItem = selectedPath
    }

    inner class GhidraInstallationValidator : FacetEditorValidator(), SlowFacetEditorValidator {
        override fun check(): ValidationResult {
            val ghidraInstallation = installationDir.get()

            if (!isGhidraInstallationPath(ghidraInstallation)) {
                return ValidationResult(GhidraBundle.message("ghidra.facet.editor.installation.error.no-properties"))
            }

            if (isGhidraSourcesPath(ghidraInstallation)) {
                return ValidationResult(GhidraBundle.message("ghidra.facet.editor.installation.error.sources"))
            }

            return ValidationResult.OK
        }
    }

    @Nls(capitalization = Nls.Capitalization.Title)
    override fun getDisplayName(): String = GhidraFacetType.FACET_NAME

    override fun isModified(): Boolean = state.installationPath != installationDir.get()

    @Throws(ConfigurationException::class)
    override fun apply() {
        try {
            val registeredInstallation =
                installationRegistry.register(Path.of(installationDir.get()))

            val ghidraLibraryName =
                "Ghidra ${registeredInstallation.version} " +
                    "${registeredInstallation.releaseName} " +
                    "(${registeredInstallation.canonicalPath})"

            state.installationPath = registeredInstallation.configuredPath.toString()
            state.resolve()

            settingsDir.set(state.settingsPath.orEmpty())
            state.version?.let(version::set)
            applied.set(true)

            runWriteAction {
                val rootManager = ModuleRootManager.getInstance(context.module)
                val vfs = VirtualFileManager.getInstance()

                fun Path.toVfs(): VirtualFile? =
                    vfs.refreshAndFindFileByUrl(VfsUtil.getUrlForLibraryRoot(this))

                val classRoots: List<VirtualFile> = state.modules.orEmpty()
                    .values
                    .map { Paths.get(it, "lib") }
                    .filter(Files::isDirectory)
                    .map(Files::list)
                    .flatMap { it.use { stream -> stream.toList() }}
                    .filter {
                        val fileName = it.fileName.toString()
                        fileName.endsWith(".jar", ignoreCase = true) && !fileName.endsWith("-src.zip", ignoreCase = true)
                    }
                    .mapNotNull(Path::toVfs)


                val sourceRoots: List<VirtualFile> =
                    state.modules.orEmpty().asSequence()
                        .map { (moduleName, moduleRootStr) -> Paths.get(moduleRootStr, "lib", "${moduleName}-src.zip") }
                        .mapNotNull(Path::toVfs)
                        .toList()

                val documentationRoots: List<VirtualFile> =
                    listOfNotNull(registeredInstallation.apiJavadocPath.toVfs())

                val registrar = LibraryTablesRegistrar.getInstance()
                val projectLibTable = registrar.getLibraryTable(context.project)
                val libModel = projectLibTable.modifiableModel
                val ghidraLib = projectLibTable.getLibraryByName(ghidraLibraryName)
                    ?: libModel.createLibrary(ghidraLibraryName)
                libModel.commit()

                ghidraLib.modifiableModel.apply {
                    listOf(
                        OrderRootType.CLASSES,
                        OrderRootType.SOURCES,
                        OrderRootType.DOCUMENTATION,
                    ).forEach { rootType ->
                        getUrls(rootType).forEach { removeRoot(it, rootType) }
                    }

                    classRoots.forEach { addRoot(it, OrderRootType.CLASSES) }
                    sourceRoots.forEach { addRoot(it, OrderRootType.SOURCES) }
                    documentationRoots.forEach { addRoot(it, OrderRootType.DOCUMENTATION) }
                }.commit()

                rootManager.modifiableModel.apply {
                    orderEntries
                        .filterIsInstance<LibraryOrderEntry>()
                        .filter { libraryEntry ->
                            libraryEntry.libraryName == "Ghidra" ||
                                libraryEntry.libraryName?.startsWith("Ghidra ") == true
                        }
                        .forEach(::removeOrderEntry)

                    addLibraryEntry(ghidraLib)
                }.commit()
            }
        } catch (e: ConfigurationException) {
            throw ConfigurationException(e.localizedMessage)
        }
    }
}