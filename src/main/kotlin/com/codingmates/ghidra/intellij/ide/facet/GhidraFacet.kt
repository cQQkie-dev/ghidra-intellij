package com.codingmates.ghidra.intellij.ide.facet


import com.intellij.facet.Facet
import com.intellij.facet.FacetManager
import com.intellij.facet.FacetType
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project

class GhidraFacet(
    facetType: FacetType<out Facet<*>, *>,
    module: Module,
    name: String,
    configuration: GhidraFacetConfiguration,
    underlyingFacet: Facet<*>?
) : Facet<GhidraFacetConfiguration>(facetType, module, name, configuration, underlyingFacet) {

    val installationPath: String
        get() = configuration.ghidraState.installationPath

    companion object {
        fun findAnyInProject(project: Project): GhidraFacet? = ModuleManager.getInstance(project)
            .modules.firstNotNullOfOrNull { FacetManager.getInstance(it).getFacetByType(FACET_TYPE_ID) }
    }
}