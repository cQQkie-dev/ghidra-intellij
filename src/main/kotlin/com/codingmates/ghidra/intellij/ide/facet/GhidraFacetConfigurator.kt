package com.codingmates.ghidra.intellij.ide.facet

import com.codingmates.ghidra.intellij.ide.installation.GhidraInstallation
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Configures a Ghidra facet state and its associated IntelliJ project library.
 *
 * The Ghidra library deliberately contains classes, sources, and Javadocs. Although Gradle
 * imports the Ghidra JARs for Gradle-backed projects, IntelliJ requires matching class roots
 * in this library to associate Ghidra's source ZIPs and API Javadocs with those classes.
 */
object GhidraFacetConfigurator {

    fun configure(
        project: Project,
        module: Module,
        state: GhidraFacetSettings,
        installation: GhidraInstallation,
    ) {
        val ghidraLibraryName =
            "Ghidra ${installation.version} " +
                "${installation.releaseName} " +
                "(${installation.canonicalPath})"

        state.installationPath = installation.configuredPath.toString()
        state.resolve()

        runWriteAction {
            val rootManager = ModuleRootManager.getInstance(module)
            val vfs = VirtualFileManager.getInstance()

            fun Path.toVfs(): VirtualFile? =
                vfs.refreshAndFindFileByUrl(VfsUtil.getUrlForLibraryRoot(this))

            val classRoots: List<VirtualFile> = state.modules.orEmpty()
                .values
                .map { Paths.get(it, "lib") }
                .filter(Files::isDirectory)
                .map(Files::list)
                .flatMap { paths -> paths.use { it.toList() } }
                .filter { path ->
                    val fileName = path.fileName.toString()

                    fileName.endsWith(".jar", ignoreCase = true) &&
                        !fileName.endsWith("-src.zip", ignoreCase = true)
                }
                .mapNotNull(Path::toVfs)

            val sourceRoots: List<VirtualFile> = state.modules.orEmpty()
                .asSequence()
                .map { (moduleName, moduleRoot) ->
                    Paths.get(moduleRoot, "lib", "$moduleName-src.zip")
                }
                .mapNotNull(Path::toVfs)
                .toList()

            val documentationRoots: List<VirtualFile> =
                listOfNotNull(installation.apiJavadocPath.toVfs())

            val libraryTable = LibraryTablesRegistrar.getInstance()
                .getLibraryTable(project)

            val libraryModel = libraryTable.modifiableModel
            val ghidraLibrary = libraryTable.getLibraryByName(ghidraLibraryName)
                ?: libraryModel.createLibrary(ghidraLibraryName)

            libraryModel.commit()

            ghidraLibrary.modifiableModel.apply {
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
                    .filter { entry ->
                        entry.libraryName == "Ghidra" ||
                            entry.libraryName?.startsWith("Ghidra ") == true
                    }
                    .forEach(::removeOrderEntry)

                addLibraryEntry(ghidraLibrary)
            }.commit()

            removeUnusedGhidraProjectLibraries(
                project = project,
                libraryTable = libraryTable,
                retainedLibrary = ghidraLibrary,
            )
        }
    }

    /**
     * Removes stale project-level Ghidra libraries after a facet target change.
     *
     * A library is removed only when no loaded project module references it. This keeps
     * independently configured modules intact while cleaning old Ghidra installations left by
     * target migration in ordinary single-root extension projects.
     */
    private fun removeUnusedGhidraProjectLibraries(
        project: Project,
        libraryTable: com.intellij.openapi.roots.libraries.LibraryTable,
        retainedLibrary: com.intellij.openapi.roots.libraries.Library,
    ) {
        val referencedLibraries = ModuleManager.getInstance(project)
            .modules
            .asSequence()
            .flatMap { projectModule ->
                ModuleRootManager.getInstance(projectModule)
                    .orderEntries
                    .asSequence()
            }
            .filterIsInstance<LibraryOrderEntry>()
            .mapNotNull(LibraryOrderEntry::getLibrary)
            .filter { library ->
                library.table == libraryTable
            }
            .toSet()

        val staleLibraries = libraryTable.libraries
            .asSequence()
            .filter { library ->
                val name = library.name

                name == "Ghidra" ||
                    name?.startsWith("Ghidra ") == true
            }
            .filter { library ->
                library != retainedLibrary &&
                    library !in referencedLibraries
            }
            .toList()

        if (staleLibraries.isEmpty()) {
            return
        }

        libraryTable.modifiableModel.apply {
            staleLibraries.forEach(::removeLibrary)
            commit()
        }
    }
}