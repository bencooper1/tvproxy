package com.tvproxy.app.data.db

import androidx.room.TypeConverter
import com.tvproxy.app.core.model.CatchupType
import com.tvproxy.app.core.model.PlaylistType
import com.tvproxy.app.core.model.RecordingState

/** Room converters for the core model enums (stored by name for forward compatibility). */
class Converters {

    @TypeConverter
    fun playlistTypeToString(value: PlaylistType): String = value.name

    @TypeConverter
    fun stringToPlaylistType(value: String): PlaylistType =
        PlaylistType.entries.firstOrNull { it.name == value } ?: PlaylistType.M3U

    @TypeConverter
    fun catchupTypeToString(value: CatchupType): String = value.name

    @TypeConverter
    fun stringToCatchupType(value: String): CatchupType =
        CatchupType.entries.firstOrNull { it.name == value } ?: CatchupType.NONE

    @TypeConverter
    fun recordingStateToString(value: RecordingState): String = value.name

    @TypeConverter
    fun stringToRecordingState(value: String): RecordingState =
        RecordingState.entries.firstOrNull { it.name == value } ?: RecordingState.FAILED
}
