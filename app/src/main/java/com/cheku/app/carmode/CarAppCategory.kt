package com.cheku.app.carmode

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.service.media.MediaBrowserService

internal enum class CarAppCategory {
    /** 地图 / 导航 / 路线规划 */
    Map,

    /** 音乐播放器 */
    Music,

    /** 其余一切 */
    Other
}

/** 地图 / 导航 */
private val MAP_PACKAGES: Set<String> = setOf(
    "com.autonavi.amapauto",        // 高德地图车机版
    "com.autonavi.minimap",         // 高德地图手机版
    "com.autonavi.xmgd.navigator",  // 高德导航（旧独立版）
    "com.baidu.BaiduMap",           // 百度地图手机版
    "com.baidu.naviauto",
    "com.tencent.map",              // 腾讯地图手机版
    "com.tencent.wecarnavi",
    "com.tencent.mapauto",
    "com.mapbar.android.mapbarmap", // 图吧导航
    "com.sogou.map.android.maps",   // 搜狗地图（已停服，保留以兼容旧装机）
    "com.huawei.petalmaps",         // 花瓣地图 Petal Maps
    "com.google.android.apps.maps",     // Google 地图（Automotive 同包名）
    "com.google.android.apps.mapslite", // Google 地图 Go
    "com.waze",
    "com.here.app.maps",                // HERE WeGo
    "com.tomtom.gplay.navapp",          // TomTom GO Navigation
    "com.tomtom.gplay.amigo",           // TomTom AmiGO
    "com.sygic.aura",                   // Sygic GPS Navigation
    "app.organicmaps",                  // Organic Maps
    "com.mapswithme.maps.pro",          // MAPS.ME
    "net.osmand",                       // OsmAnd
    "net.osmand.plus",                  // OsmAnd+
    "com.generalmagic.magicearth",      // Magic Earth
    "ru.yandex.yandexmaps",             // Yandex Maps
    "ru.yandex.yandexnavi",             // Yandex Navigator
    "ru.dublgis.dgismobile",            // 2GIS
    "com.nhn.android.nmap",             // Naver 地图
    "net.daum.android.map",             // Kakao 地图
    "com.skt.tmap.ku",                  // T map
    "cz.seznam.mapy",                   // Mapy.cz
    "menion.android.locus",             // Locus Map
    "menion.android.locus.pro",         // Locus Map Pro
    "com.gurumaps.app",                 // Guru Maps
    "com.mapquest.android.ace"          // MapQuest
)

/** 地图 / 导航 */
private val MAP_PACKAGE_PREFIXES: List<String> = listOf(
    "cld.navi.",                        // 凯立德导航
    "com.alk.copilot.marketplace."      // CoPilot GPS
)

/** 音乐播放器 */
private val MUSIC_PACKAGES: Set<String> = setOf(
    "com.tencent.qqmusic",          // QQ音乐
    "com.tencent.qqmusiccar",
    "com.netease.cloudmusic",
    "com.kugou.android",            // 酷狗音乐
    "com.kugou.android.auto",
    "cn.kuwo.player",               // 酷我音乐
    "cn.kuwo.kwmusiccar",
    "com.luna.music",
    "cn.toside.music.mobile",       // LX Music（本机装过）
    "cmccwm.mobilemusic",           // 咪咕音乐
    "com.hiby.music",               // 海贝音乐
    "com.huawei.music",             // 华为音乐（车机预装常见）
    "com.miui.player",              // 小米音乐
    "com.oppo.music",               // OPPO 音乐
    "com.android.bbkmusic",         // vivo i音乐
    "com.sec.android.app.music",    // 三星音乐
    "com.meizu.media.music",        // 魅族音乐
    "com.oneplus.music",            // 一加音乐
    "com.spotify.music",                        // Spotify
    "com.spotify.lite",                         // Spotify Lite
    "com.google.android.apps.youtube.music",    // YouTube Music
    "com.apple.android.music",                  // Apple Music
    "com.amazon.mp3",                           // Amazon Music
    "deezer.android.app",
    "com.aspiro.tidal",                         // TIDAL
    "com.soundcloud.android",                   // SoundCloud
    "com.maxmpz.audioplayer",                   // Poweramp
    "org.videolan.vlc",                         // VLC
    "com.aimp.player",
    "in.krosbits.musicolet",                    // Musicolet
    "com.jetappfactory.jetaudio",               // jetAudio
    "com.neutroncode.mp",                       // Neutron
    "code.name.monkey.retromusic",              // Retro Music
    "com.musicplayer.blackplayerfree",          // BlackPlayer
    "another.music.player",                     // Shuttle
    "ru.yandex.music",                          // Yandex Music
    "com.naver.vibe",                           // Naver VIBE
    "com.iloen.melon",                          // Melon
    "com.ktmusic.geniemusic",                   // Genie
    "com.neowiz.android.bugs",                  // Bugs
    "com.skysoft.kkbox.android",                // KKBOX
    "com.tencent.ibg.joox",                     // JOOX
    "com.gaana",                                // Gaana
    "com.jio.media.jiobeats",                   // JioSaavn
    "com.bsbportal.music",                      // Wynk
    "com.anghami",                              // Anghami
    "com.afmobi.boomplayer"                     // Boomplay
)

private val NOT_CATEGORIZED_PACKAGES: Set<String> = setOf(
    // 电台
    "tunein.player",
    "com.clearchannel.iheartradio",
    "com.pandora.android",
    "com.sirius",
    // 播客
    "com.bambuna.podcastaddict",
    "de.danoeh.antennapod",
    "au.com.shiftyjelly.pocketcasts",
    // 有声书 / 听书
    "com.audible.application",
    "com.ximalaya.ting.android",    // 喜马拉雅
    "com.yibasan.lizhifm",          // 荔枝 FM
    "com.qingting.fm",              // 蜻蜓 FM
    "com.dragon.read",              // 番茄畅听
    "com.tencent.qqfm",             // 企鹅 FM
    // 听歌识曲（不是播放器）
    "com.shazam.android",
    // 手机 ⇄ 车机互联 / 投屏（不是导航本体）
    "com.baidu.carlife",                        // 百度 CarLife
    "com.google.android.projection.gearhead"    // Android Auto
)

private val MAP_KEYWORDS: List<String> =
    listOf("地图", "导航", "map", "navi", "navigation", "gps", "route", "路线")

private val MUSIC_KEYWORDS: List<String> =
    listOf("音乐", "music", "player", "播放器", "播放", "歌曲", "歌单", "歌")

internal class CarAppCategoryIndex(
    private val geoPackages: Set<String>,
    private val appMapsPackages: Set<String>,
    private val mediaBrowserPackages: Set<String>,
    private val appMusicPackages: Set<String>,
    private val audioViewPackages: Set<String>
) {
    /** 判断一个应用属于哪一类 */
    fun classify(packageName: String, label: String): CarAppCategory {
        if (packageName in NOT_CATEGORIZED_PACKAGES) return CarAppCategory.Other

        val looksLikeMap = label.containsAnyKeyword(MAP_KEYWORDS)
        val isMap = packageName in MAP_PACKAGES ||
            MAP_PACKAGE_PREFIXES.any { packageName.startsWith(it) } ||
            packageName in appMapsPackages ||
            (packageName in geoPackages && looksLikeMap)
        if (isMap) return CarAppCategory.Map

        val looksLikeMusic = label.containsAnyKeyword(MUSIC_KEYWORDS)
        val isMusic = packageName in MUSIC_PACKAGES ||
            packageName in appMusicPackages ||
            (packageName in mediaBrowserPackages && looksLikeMusic) ||
            (packageName in audioViewPackages && looksLikeMusic)
        if (isMusic) return CarAppCategory.Music

        return CarAppCategory.Other
    }
}

/** 名称里是否含任一关键词 */
private fun String.containsAnyKeyword(keywords: List<String>): Boolean {
    val lower = lowercase()
    return keywords.any { lower.contains(it) }
}

/** 扫一遍本机、把探测信号压成索引 */
internal fun buildCarAppCategoryIndex(pm: PackageManager): CarAppCategoryIndex {
    val geo = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=0,0"))
    val appMaps = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MAPS)
    val appMusic = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC)
    val mediaBrowser = Intent(MediaBrowserService.SERVICE_INTERFACE)
    val audioView = Intent(Intent.ACTION_VIEW).setType("audio/*")

    return CarAppCategoryIndex(
        geoPackages = pm.packagesRespondingTo(geo),
        appMapsPackages = pm.packagesRespondingTo(appMaps),
        mediaBrowserPackages = pm.packagesProvidingService(mediaBrowser),
        appMusicPackages = pm.packagesRespondingTo(appMusic),
        audioViewPackages = pm.packagesRespondingTo(audioView)
    )
}

private fun PackageManager.packagesRespondingTo(intent: Intent): Set<String> = runCatching {
    queryIntentActivities(intent, 0)
        .mapNotNull { it.activityInfo?.packageName }
        .toSet()
}.getOrDefault(emptySet())

private fun PackageManager.packagesProvidingService(intent: Intent): Set<String> = runCatching {
    queryIntentServices(intent, 0)
        .mapNotNull { it.serviceInfo?.packageName }
        .toSet()
}.getOrDefault(emptySet())
