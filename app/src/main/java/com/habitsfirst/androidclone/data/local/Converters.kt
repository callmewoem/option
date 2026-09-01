package com.habitsfirst.androidclone.data.local

import androidx.room.TypeConverter
import com.habitsfirst.androidclone.domain.model.BlockListSource
import com.habitsfirst.androidclone.domain.model.BlockMode

/** Room enum <-> String converters for the URL block list tables. */
class Converters {
    @TypeConverter
    fun fromBlockListSource(value: BlockListSource): String = value.name

    @TypeConverter
    fun toBlockListSource(value: String): BlockListSource =
        runCatching { BlockListSource.valueOf(value) }.getOrDefault(BlockListSource.CUSTOM)

    @TypeConverter
    fun fromBlockMode(value: BlockMode): String = value.name

    @TypeConverter
    fun toBlockMode(value: String): BlockMode =
        runCatching { BlockMode.valueOf(value) }.getOrDefault(BlockMode.GATED)
}
