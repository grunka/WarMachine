package se.grunka.basal;

import ch.qos.logback.classic.Level;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class LevelTypeAdapterTest {

    private Gson gson;

    @Before
    public void before() throws Exception {
        gson = new GsonBuilder().registerTypeAdapter(Level.class, new LevelTypeAdapter()).create();
    }

    @Test
    public void shouldDeserialize() throws Exception {
        Level level = gson.fromJson("\"ERROR\"", Level.class);
        assertEquals(Level.ERROR, level);
    }

    @Test
    public void shouldSerialize() throws Exception {
        String json = gson.toJson(Level.DEBUG);
        assertEquals("\"DEBUG\"", json);
    }

    @Test(expected = JsonParseException.class)
    public void shouldFailForUnknownString() throws Exception {
        gson.fromJson("\"WHAT?\"", Level.class);
    }

    @Test(expected = JsonParseException.class)
    public void shouldFailForObject() throws Exception {
        gson.fromJson("{}", Level.class);
    }
}
