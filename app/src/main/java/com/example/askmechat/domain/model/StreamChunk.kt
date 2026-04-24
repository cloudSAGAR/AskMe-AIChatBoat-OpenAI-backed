package com.example.askmechat.domain.model

/**
 * Streaming contract between the data layer and the presentation layer.
 *
 * The repository exposes a `Flow<StreamChunk>` for each chat request; the
 * ViewModel reduces these chunks into visible message state. Keeping this
 * in the domain layer means neither side depends on the other's
 * implementation details.
 */
sealed class StreamChunk {
    /** Request acknowledged, nothing on the wire yet. */
    object Loading : StreamChunk()

    /** Incremental text update — [accumulatedText] always holds the full so-far. */
    data class Streaming(val accumulatedText: String) : StreamChunk()

    /** Final, complete text answer. */
    data class Success(val fullText: String) : StreamChunk()

    /** Stream was aborted mid-way. [partialText] holds what we received. */
    data class PartialSuccess(val partialText: String) : StreamChunk()

    /** Visualization block (e.g. map data) attached to this answer. */
    data class MapData(val points: List<MapPoint>) : StreamChunk()

    /** Unrecoverable error — [message] is user-safe. */
    data class Error(val message: String) : StreamChunk()
}
