package se.grunka.warmachine;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import org.apache.log4j.Level;

import java.lang.reflect.Type;

public class LevelTypeAdapter implements JsonDeserializer<Level>, JsonSerializer<Level>{
    @Override
    public Level deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        Level level = Level.toLevel(json.getAsString(), null);
        if (level == null) {
            throw new JsonParseException("Unknown log level " + json.getAsString());
        }
        return level;
    }

    @Override
    public JsonElement serialize(Level src, Type typeOfSrc, JsonSerializationContext context) {
        return new JsonPrimitive(src.toString());
    }
}
