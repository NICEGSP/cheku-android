package com.cheku.app.glass

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.cheku.app.glass.components.glassInnerShadowOf
import com.kyant.backdrop.shadow.InnerShadow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassTokensContractTest {
    private val levels = GlassLevel.entries

    // ---- 1. completeness + level backfill (no missing / misrouted tiers) ----

    @Test
    fun `every GlassLevel has a specFor result and the level field is backfilled correctly`() {
        assertEquals(5, levels.size)
        for (lvl in levels) {
            val spec = GlassTokens.specFor(lvl)
            assertEquals("specFor($lvl).level must echo the requested level", lvl, spec.level)
        }
    }

    @Test
    fun `blur increases monotonically with glass level`() {
        fun blurOf(l: GlassLevel) = GlassTokens.specFor(l).blurRadiusDp
        assertTrue(
            "L1(一层卡片) 必须比 L2 更少磨砂",
            blurOf(GlassLevel.L1_Card) < blurOf(GlassLevel.L2_Panel)
        )
        assertTrue(
            "L2 必须比 S1(二层控件) 更少磨砂",
            blurOf(GlassLevel.L2_Panel) < blurOf(GlassLevel.S1_Control)
        )
        assertTrue(
            "S1(二层控件) 不能比 L4(浮层) 更磨砂",
            blurOf(GlassLevel.S1_Control) <= blurOf(GlassLevel.L4_Nav)
        )
        assertEquals(
            "L4 与 L3 同属浮层档，磨砂应一致",
            blurOf(GlassLevel.L4_Nav),
            blurOf(GlassLevel.L3_Dialog),
            0.0001f
        )
    }

    @Test
    fun `S1_Control uses the single controlBlurDp value`() {
        assertEquals(7f, GlassTokens.controlBlurDp, 0.0001f)
        assertEquals(
            GlassTokens.controlBlurDp,
            GlassTokens.specFor(GlassLevel.S1_Control).blurRadiusDp,
            0.0001f
        )
    }

    @Test
    fun `shapeCard is 20dp`() = assertCornerSize(GlassTokens.shapeCard, 20)

    @Test
    fun `shapeControl is 16dp`() = assertCornerSize(GlassTokens.shapeControl, 16)

    @Test
    fun `shapeDialog is 48dp per reference DialogContent`() = assertCornerSize(GlassTokens.shapeDialog, 48)

    @Test
    fun `shapeNav is 28dp`() = assertCornerSize(GlassTokens.shapeNav, 28)

    @Test
    fun `L1 and L2 resolve to shapeCard`() {
        assertEquals(GlassTokens.shapeCard, GlassTokens.specFor(GlassLevel.L1_Card).shape)
        assertEquals(GlassTokens.shapeCard, GlassTokens.specFor(GlassLevel.L2_Panel).shape)
    }

    @Test
    fun `L3 resolves to shapeDialog`() {
        assertEquals(GlassTokens.shapeDialog, GlassTokens.specFor(GlassLevel.L3_Dialog).shape)
    }

    @Test
    fun `L4 resolves to shapeNav`() {
        assertEquals(GlassTokens.shapeNav, GlassTokens.specFor(GlassLevel.L4_Nav).shape)
    }

    // ---- 3. "declared refraction but no params" is forbidden ----

    @Test
    fun `any level whose lens is not None must carry a non-null lensSpec`() {
        for (lvl in levels) {
            val spec = GlassTokens.specFor(lvl)
            if (spec.lens != GlassLens.None) {
                assertNotNull("${lvl.name}: lens=${spec.lens} but lensSpec is null", spec.lensSpec)
            }
        }
    }

    @Test
    fun `L1 and L2 must NOT enable chromatic aberration`() {
        assertFalse(GlassTokens.specFor(GlassLevel.L1_Card).lensSpec!!.chromaticAberration)
        assertFalse(GlassTokens.specFor(GlassLevel.L2_Panel).lensSpec!!.chromaticAberration)
    }

    @Test
    fun `L3 and L4 must enable chromatic aberration and depth`() {
        for (lvl in listOf(GlassLevel.L3_Dialog, GlassLevel.L4_Nav)) {
            val ls = GlassTokens.specFor(lvl).lensSpec!!
            assertTrue("${lvl.name}: chromaticAberration must be true", ls.chromaticAberration)
            assertTrue("${lvl.name}: depth must be true", ls.depth)
        }
    }

    // ---- 5. consumesSharedCapture ----

    @Test
    fun `L2 L3 L4 consume the shared capture`() {
        assertTrue(GlassTokens.specFor(GlassLevel.L2_Panel).consumesSharedCapture)
        assertTrue(GlassTokens.specFor(GlassLevel.L3_Dialog).consumesSharedCapture)
        assertTrue(GlassTokens.specFor(GlassLevel.L4_Nav).consumesSharedCapture)
    }

    @Test
    fun `L1 also reuses the shared capture per PRD 5-1 (复用共享捕获)`() {
        assertTrue(GlassTokens.specFor(GlassLevel.L1_Card).consumesSharedCapture)
    }

    @Test
    fun `L4 nav blurRadiusDp is 11f per layer-monotonic fix`() {
        assertEquals(11f, GlassTokens.specFor(GlassLevel.L4_Nav).blurRadiusDp, 0.0001f)
    }

    @Test
    fun `L1 card is fully transparent and unblurred`() {
        val spec = GlassTokens.specFor(GlassLevel.L1_Card)
        assertEquals("L1 磨砂必须为 0", 0f, spec.blurRadiusDp, 0.0001f)
        assertEquals("L1 洗色必须完全透明", 0f, GlassTokens.cardFillAlpha, 0.0001f)
        assertTrue("0 磨砂不等于不采样背景", spec.consumesSharedCapture)
    }

    @Test
    fun `tiers above the card stay heavier than the card but lighter than before`() {
        fun blurOf(l: GlassLevel) = GlassTokens.specFor(l).blurRadiusDp
        val above = listOf(GlassLevel.L2_Panel, GlassLevel.S1_Control, GlassLevel.L4_Nav, GlassLevel.L3_Dialog)
        for (lvl in above) {
            assertTrue("$lvl 必须比底层卡片更磨砂", blurOf(lvl) > blurOf(GlassLevel.L1_Card))
        }
        assertTrue(
            "二层控件必须比底层卡片更不透明",
            GlassTokens.controlFillAlpha > GlassTokens.cardFillAlpha
        )
        assertEquals("L2 磨砂按本轮下调到 3dp", 3f, blurOf(GlassLevel.L2_Panel), 0.0001f)
        assertEquals("S1 磨砂按本轮下调到 7dp", 7f, blurOf(GlassLevel.S1_Control), 0.0001f)
    }

    // ---- 7. alpha lambdas stay within [0,1] (range only; no press-composition logic) ----

    @Test
    fun `all alpha lambdas return a value within zero and one inclusive`() {
        for (lvl in levels) {
            val spec = GlassTokens.specFor(lvl)
            spec.highlight?.alpha()?.let { v ->
                assertTrue("${lvl.name} highlight.alpha=$v outside [0,1]", v in 0f..1f)
            }
            spec.shadow?.alpha()?.let { v ->
                assertTrue("${lvl.name} shadow.alpha=$v outside [0,1]", v in 0f..1f)
            }
            spec.innerShadow?.alpha()?.let { v ->
                assertTrue("${lvl.name} innerShadow.alpha=$v outside [0,1]", v in 0f..1f)
            }
        }
    }

    // ---- 8. specFor is a pure function ----

    @Test
    fun `specFor is a pure function (repeated calls are structurally equal)`() {
        for (lvl in levels) {
            assertSpecStructurallyEqual(GlassTokens.specFor(lvl), GlassTokens.specFor(lvl))
        }
    }

    @Test
    fun `L4 container lensSpec is 24dp by 24dp per the reference implementation`() {
        val ls = GlassTokens.specFor(GlassLevel.L4_Nav).lensSpec!!
        assertEquals(24f, ls.heightDp, 0.0001f)
        assertEquals(24f, ls.amountDp, 0.0001f)
    }

    @Test
    fun `L4 lensSpec must NOT equal L3 (guards against re-copying L3 params)`() {
        assertNotEquals(
            GlassTokens.specFor(GlassLevel.L3_Dialog).lensSpec,
            GlassTokens.specFor(GlassLevel.L4_Nav).lensSpec
        )
    }

    // ---- 10. L4 inner shadow must not exist at rest, and follow the reference at full press ----

    @Test
    fun `L4 inner shadow is null at rest (static alpha is 0)`() {
        assertNull(glassInnerShadowOf(GlassTokens.specFor(GlassLevel.L4_Nav), 0f))
    }

    @Test
    fun `L4 inner shadow at full press is InnerShadow(8dp, alpha=1) per reference`() {
        val ish = glassInnerShadowOf(GlassTokens.specFor(GlassLevel.L4_Nav), 1f)
        assertNotNull(ish)
        assertEquals(1f, ish!!.alpha, 0.0001f)
        assertEquals(8.dp, ish.radius)
    }

    // ---- 11. the L4 fix must not have broken the static-alpha tiers ----

    @Test
    fun `L3 inner shadow at rest keeps its static alpha 0-18 (fix regression guard)`() {
        val ish = glassInnerShadowOf(GlassTokens.specFor(GlassLevel.L3_Dialog), 0f)
        assertNotNull(ish)
        assertEquals(0.18f, ish!!.alpha, 0.0001f)
    }

    @Test
    fun `L2 inner shadow at rest keeps its static alpha 0-12 (fix regression guard)`() {
        val ish = glassInnerShadowOf(GlassTokens.specFor(GlassLevel.L2_Panel), 0f)
        assertNotNull(ish)
        assertEquals(0.12f, ish!!.alpha, 0.0001f)
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private fun assertSpecStructurallyEqual(a: GlassSpec, b: GlassSpec) {
        assertEquals(a.level, b.level)
        assertEquals(a.blurRadiusDp, b.blurRadiusDp, 1e-6f)
        assertEquals(a.lens, b.lens)
        assertEquals(a.lensSpec, b.lensSpec)
        assertEquals(a.shape, b.shape)
        assertEquals(a.consumesSharedCapture, b.consumesSharedCapture)
        assertNotNull(a.fill)
        assertNotNull(b.fill)

        compareHighlight(a.highlight, b.highlight)
        compareShadow(a.shadow, b.shadow)
        compareInnerShadow(a.innerShadow, b.innerShadow)
    }

    private fun compareHighlight(a: GlassHighlightSpec?, b: GlassHighlightSpec?) {
        if (a == null) {
            assertNull(b)
            return
        }
        assertNotNull(b)
        val other = b!!
        assertEquals(a.widthDp, other.widthDp, 1e-6f)
        assertEquals(a.falloff, other.falloff, 1e-6f)
        assertEquals(a.style, other.style)
        assertEquals(a.alpha(), other.alpha(), 1e-6f)
    }

    private fun compareShadow(a: GlassShadowSpec?, b: GlassShadowSpec?) {
        if (a == null) {
            assertNull(b)
            return
        }
        assertNotNull(b)
        val other = b!!
        assertEquals(a.radiusDp, other.radiusDp, 1e-6f)
        assertEquals(a.alpha(), other.alpha(), 1e-6f)
    }

    private fun compareInnerShadow(a: GlassInnerShadowSpec?, b: GlassInnerShadowSpec?) {
        if (a == null) {
            assertNull(b)
            return
        }
        assertNotNull(b)
        val other = b!!
        assertEquals(a.radiusDp, other.radiusDp, 1e-6f)
        assertEquals(a.alpha(), other.alpha(), 1e-6f)
    }

    private fun assertCornerSize(shape: Shape, expectedDp: Int) {
        val actual = (shape as CornerBasedShape).topStart
        val expected = RoundedCornerShape(expectedDp.dp).topStart
        assertEquals("shape corner must be ${expectedDp}dp", expected, actual)
    }
}
