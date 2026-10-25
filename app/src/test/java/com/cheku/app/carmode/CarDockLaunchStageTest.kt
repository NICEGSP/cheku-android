package com.cheku.app.carmode

import org.junit.Assert.assertEquals
import org.junit.Test

class CarDockLaunchStageTest {
    @Test
    fun firstClickEntersSplit() {
        assertEquals(
            CarDockLaunchStage.Split,
            nextDockLaunchStage(null, CarDockLaunchStage.None, CarDockItem.Music)
        )
    }

    @Test
    fun secondClickGoesFullscreen() {
        assertEquals(
            CarDockLaunchStage.Fullscreen,
            nextDockLaunchStage(CarDockItem.Music, CarDockLaunchStage.Split, CarDockItem.Music)
        )
    }

    @Test
    fun thirdClickReturnsToSplit() {
        assertEquals(
            CarDockLaunchStage.Split,
            nextDockLaunchStage(CarDockItem.Music, CarDockLaunchStage.Fullscreen, CarDockItem.Music)
        )
    }

    @Test
    fun switchingToAnotherItemRestartsAtSplit() {
        assertEquals(
            CarDockLaunchStage.Split,
            nextDockLaunchStage(CarDockItem.Music, CarDockLaunchStage.Fullscreen, CarDockItem.Nav)
        )
    }

    @Test
    fun navFollowsTheSameCycle() {
        assertEquals(
            CarDockLaunchStage.Split,
            nextDockLaunchStage(CarDockItem.Nav, CarDockLaunchStage.Fullscreen, CarDockItem.Nav)
        )
    }
}
