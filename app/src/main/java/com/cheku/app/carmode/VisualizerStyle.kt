package com.cheku.app.carmode

/** 频谱样式 */
internal enum class VisualizerStyle(
    val key: String,
    val label: String
) {
    CURVE("curve", "水平波浪"),

    MIRROR_BARS("mirror_bars", "双向柱"),

    SYMMETRIC_WAVE("sym_wave", "对称波形"),

    BARS("bars", "频谱柱"),

    RADIAL_BARS("radial_bars", "环形柱"),

    SMOOTH_WAVE("smooth_wave", "平滑波纹"),

    FILL_WAVE("fill_wave", "填充波形"),

    MIRROR_SPECTRUM("mirror_spec", "左右镜像"),

    BLOCKS("blocks", "波形块"),

    DOTS("dots", "点阵"),

    RING_WAVE("ring_wave", "环形波形"),

    RING_DOTS("ring_dots", "圆点环"),

    BLOB("blob", "流体团块"),

    BLAST("blast", "放射爆发"),

    DUAL_RING("dual_ring", "双环反向"),

    SQUARE_BARS("square_bars", "方块柱"),

    THIN_BARS("thin_bars", "细线柱"),

    STEPS("steps", "阶梯条"),

    RADAR("radar", "雷达扫描"),

    HEATMAP("heatmap", "热力栅格"),
    ;

    companion object {
        /** 按 key 解析 */
        fun from(key: String?): VisualizerStyle =
            entries.firstOrNull { it.key == key } ?: CURVE
    }
}
