package com.smart.erp.spike.s1.jobs;

import com.github.kagkarlsson.scheduler.serializer.Serializer;
import tools.jackson.databind.json.JsonMapper;

/** Task data as JSON (Jackson 3, Boot's mapper): readable in the platform DB, no Java serialization. */
final class JsonTaskDataSerializer implements Serializer {

    private final JsonMapper json;

    JsonTaskDataSerializer(JsonMapper json) {
        this.json = json;
    }

    @Override
    public byte[] serialize(Object data) {
        return data == null ? null : json.writeValueAsBytes(data);
    }

    @Override
    public <T> T deserialize(Class<T> type, byte[] serializedData) {
        return serializedData == null ? null : json.readValue(serializedData, type);
    }
}
