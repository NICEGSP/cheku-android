package com.cheku.app.carmode

import org.junit.Assert.assertEquals
import org.junit.Test

class CarAppCategoryTest {
    private fun index(
        geo: Set<String> = emptySet(),
        appMaps: Set<String> = emptySet(),
        mediaBrowser: Set<String> = emptySet(),
        appMusic: Set<String> = emptySet(),
        audioView: Set<String> = emptySet()
    ) = CarAppCategoryIndex(
        geoPackages = geo,
        appMapsPackages = appMaps,
        mediaBrowserPackages = mediaBrowser,
        appMusicPackages = appMusic,
        audioViewPackages = audioView
    )

    @Test
    fun whitelistedMapPackagesAreMapsEvenWithoutAnyProbeSignal() {
        val noProbe = index()
        listOf(
            "com.autonavi.amapauto",    // 高德车机版
            "com.autonavi.minimap",     // 高德手机版
            "com.baidu.naviauto",       // 百度地图汽车版
            "com.tencent.wecarnavi",    // 腾讯智驾地图
            "com.google.android.apps.maps",
            "com.here.app.maps",
            "com.tomtom.gplay.navapp"
        ).forEach { pkg ->
            assertEquals("$pkg 应在白名单里被认成地图", CarAppCategory.Map, noProbe.classify(pkg, "随便一个名字"))
        }
    }

    @Test
    fun whitelistedMusicPackagesAreMusicEvenWithoutAnyProbeSignal() {
        val noProbe = index()
        listOf(
            "com.netease.cloudmusic",   // 网易云音乐（车机版同包名）
            "com.tencent.qqmusiccar",   // QQ音乐车机版
            "com.kugou.android.auto",   // 酷狗音乐车机版
            "cn.kuwo.kwmusiccar",       // 酷我音乐车载版
            "com.luna.music",           // 汽水音乐
            "cn.toside.music.mobile",   // LX Music
            "com.apple.android.music",
            "com.spotify.music"
        ).forEach { pkg ->
            assertEquals("$pkg 应在白名单里被认成音乐", CarAppCategory.Music, noProbe.classify(pkg, "随便一个名字"))
        }
    }

    @Test
    fun versionedMapPackageNamesAreMatchedByPrefix() {
        val index = index()
        assertEquals(CarAppCategory.Map, index.classify("cld.navi.c3788.mainframe", "凯立德导航"))
        assertEquals(CarAppCategory.Map, index.classify("cld.navi.c3535.mainframe", "凯立德"))
        // CoPilot 的地区后缀同理。
        assertEquals(CarAppCategory.Map, index.classify("com.alk.copilot.marketplace.usa", "CoPilot"))
        assertEquals(CarAppCategory.Other, index.classify("cld.navinew.app", "某个无关应用"))
    }

    @Test
    fun radioPodcastAndAudiobookStayOutOfMusicEvenWhenTheyProbeLikePlayers() {
        // 探测结果：这批包全都"像播放器"（声明了 MediaBrowserService）。
        val radioPackages = setOf(
            "com.ximalaya.ting.android",
            "com.yibasan.lizhifm",
            "com.qingting.fm",
            "tunein.player",
            "com.pandora.android",
            "com.audible.application",
            "com.bambuna.podcastaddict"
        )
        val probe = index(mediaBrowser = radioPackages)

        radioPackages.forEach { pkg ->
            assertEquals(
                "$pkg 属于电台/播客/有声书，不得出现在「默认音乐」里",
                CarAppCategory.Other,
                probe.classify(pkg, "喜马拉雅 听书 播客 电台")
            )
        }
    }

    @Test
    fun projectionAppsAreNotMaps() {
        val probe = index(geo = setOf("com.baidu.carlife", "com.google.android.projection.gearhead"))
        assertEquals(CarAppCategory.Other, probe.classify("com.baidu.carlife", "百度CarLife 导航"))
        assertEquals(
            CarAppCategory.Other,
            probe.classify("com.google.android.projection.gearhead", "Android Auto 导航")
        )
    }

    @Test
    fun geoHandlerNeedsAMapLikeName() {
        val probe = index(geo = setOf("com.example.navi", "com.example.shop"))
        assertEquals(CarAppCategory.Map, probe.classify("com.example.navi", "某某导航"))
        assertEquals(CarAppCategory.Other, probe.classify("com.example.shop", "某某商城"))
    }

    @Test
    fun explicitLauncherCategoriesAreStrongSignals() {
        // 强信号：官方给地图/音乐留的启动器分类，不需要关键词确认。
        val probe = index(appMaps = setOf("com.example.a"), appMusic = setOf("com.example.b"))
        assertEquals(CarAppCategory.Map, probe.classify("com.example.a", "Whatever"))
        assertEquals(CarAppCategory.Music, probe.classify("com.example.b", "Whatever"))
    }

    @Test
    fun mediaBrowserAndAudioViewNeedAMusicLikeName() {
        val probe = index(
            mediaBrowser = setOf("com.example.player", "com.example.video"),
            audioView = setOf("com.example.tunes")
        )
        assertEquals(CarAppCategory.Music, probe.classify("com.example.player", "某播放器"))
        assertEquals(CarAppCategory.Other, probe.classify("com.example.video", "某影院"))
        assertEquals(CarAppCategory.Music, probe.classify("com.example.tunes", "My Tunes Player"))
    }

    @Test
    fun keywordMatchingIsCaseInsensitive() {
        val probe = index(geo = setOf("com.example.app"))
        assertEquals(CarAppCategory.Map, probe.classify("com.example.app", "City MAPS"))
        assertEquals(CarAppCategory.Map, probe.classify("com.example.app", "city maps"))
    }

    @Test
    fun unknownAppIsNeitherMapNorMusic() {
        val probe = index(geo = setOf("com.example.shop"), mediaBrowser = setOf("com.example.video"))
        assertEquals(CarAppCategory.Other, probe.classify("com.tencent.mm", "微信"))
        assertEquals(CarAppCategory.Other, probe.classify("com.example.unknown", "某应用"))
    }

    @Test
    fun exclusionWinsOverEveryOtherSignal() {
        val probe = index(
            geo = setOf("com.baidu.carlife"),
            appMaps = setOf("com.baidu.carlife"),
            mediaBrowser = setOf("com.ximalaya.ting.android"),
            appMusic = setOf("com.ximalaya.ting.android")
        )
        assertEquals(CarAppCategory.Other, probe.classify("com.baidu.carlife", "导航"))
        assertEquals(CarAppCategory.Other, probe.classify("com.ximalaya.ting.android", "音乐"))
    }
}
