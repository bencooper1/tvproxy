package com.tvproxy.app.core.model

/** Recording lifecycle (see architecture.md §5.4). */
enum class RecordingState { SCHEDULED, RECORDING, DONE, FAILED, CANCELED }
