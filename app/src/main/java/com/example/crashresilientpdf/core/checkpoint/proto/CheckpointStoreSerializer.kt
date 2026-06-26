package com.example.crashresilientpdf.core.checkpoint.proto

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.google.protobuf.InvalidProtocolBufferException
import java.io.InputStream
import java.io.OutputStream

/**
 * CheckpointStoreSerializer - Phase 5
 * Proto DataStore serializer for crash-safe atomic writes.
 */
object CheckpointStoreSerializer : Serializer<CheckpointStore> {
    override val defaultValue: CheckpointStore = CheckpointStore.newBuilder()
        .setSchemaVersion(2)
        .build()

    override suspend fun readFrom(input: InputStream): CheckpointStore {
        try {
            return CheckpointStore.parseFrom(input)
        } catch (e: InvalidProtocolBufferException) {
            throw CorruptionException("Cannot read CheckpointStore proto", e)
        }
    }

    override suspend fun writeTo(t: CheckpointStore, output: OutputStream) {
        t.writeTo(output)
    }
}
