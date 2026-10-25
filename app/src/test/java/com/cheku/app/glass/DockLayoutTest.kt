package com.cheku.app.glass

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** dock 几何契约 */
class DockLayoutTest {
    /** 浮层底边距 = dock 底边距 + dock 高度 + 呼吸间距 */
    @Test
    fun floatBottomInsetCoversDockHeightMarginAndGap() {
        assertEquals("18(常规底边距) + 64(dock 高) + 8(呼吸) = 90dp", 90.dp, dockFloatBottomInset(18.dp))
        assertEquals("36(多窗口底边距) + 64 + 8 = 108dp", 108.dp, dockFloatBottomInset(36.dp))
    }

    @Test
    fun floatInsetIsStrictlyAboveDockTopEdge() {
        val dockTopEdge = DockMarginDp.dp + DockBarHeightDp.dp
        assertTrue(
            "浮层底边距必须大于 dock 顶边（否则会压住 dock）",
            dockFloatBottomInset(DockMarginDp.dp) > dockTopEdge,
        )
        assertTrue("呼吸间距必须为正", DockFloatGap > 0.dp)
        assertTrue("多窗口间距应大于常规间距", DockMarginMultiWindowDp > DockMarginDp)
    }

    @Test
    fun dockBarHeightMatchesGlassContainer() {
        assertEquals(64f, DockBarHeightDp, 0f)
    }
}
