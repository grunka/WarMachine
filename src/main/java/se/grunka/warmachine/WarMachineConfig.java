package se.grunka.warmachine;

import org.apache.log4j.Level;

import java.util.HashMap;
import java.util.Map;

public class WarMachineConfig {
    public int port = 8080;
    public Map<String, String> paths = new HashMap<String, String>();
    public Level logLevel = Level.INFO;
    public int threads = 100;
}
