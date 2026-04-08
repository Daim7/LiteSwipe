package com.liteswipe.app.data.model

/** 按日期聚合的媒体分组，用于相册列表按天展示标题与条目。 */
data class DateGroup(
    val dateKey: String,
    val displayLabel: String,
    val items: List<MediaItem>
)
