package com.vaibhav.calc.android

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class PhotoGeometryWorkspaceSpec : StringSpec({
    "an unapplied geometry draft remains separate from reviewed text and applied geometry" {
        val workspace = PhotoWorkspace()
        workspace.text = "12+34"
        val chosen = PhotoGeometryAdjustment(PhotoQuad.FULL.copy(topLeft = PhotoPoint(.1, .2)), -6.0)
        workspace.geometryEditing = true
        workspace.geometryDraft = chosen
        workspace.geometry shouldBe PhotoGeometryAdjustment.NONE
        workspace.text shouldBe "12+34"
        workspace.geometryDraft shouldBe chosen
        workspace.geometryEditing shouldBe true
    }
    "pending rendering preserves geometry and Clear releases all geometry state" {
        val workspace = PhotoWorkspace()
        val chosen = PhotoGeometryAdjustment(straightenDegrees = 7.5)
        workspace.geometry = chosen
        workspace.geometryEditing = true
        workspace.geometryDraft = chosen.copy(straightenDegrees = 4.0)
        workspace.imageAdvice = listOf("Try better lighting")
        workspace.pendingRender = PhotoRenderRequest(PhotoRegion.FULL, 1, PhotoImageTreatment.CONTRAST, chosen)
        workspace.pendingRender!!.geometry shouldBe chosen
        workspace.clear()
        workspace.pendingRender shouldBe null
        workspace.geometry shouldBe PhotoGeometryAdjustment.NONE
        workspace.geometryDraft shouldBe PhotoGeometryAdjustment.NONE
        workspace.geometryEditing shouldBe false
        workspace.geometryPreview shouldBe null
        workspace.imageAdvice shouldBe emptyList()
    }
})
