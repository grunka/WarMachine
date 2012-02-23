package se.grunka.basal;

import java.util.HashMap;
import java.util.Map;

import ch.qos.logback.classic.Level;

public class BasalConfig {
    public int port = 8080;
    public Map<String, String> paths = new HashMap<String, String>();
    public Level logLevel = Level.INFO;
}
