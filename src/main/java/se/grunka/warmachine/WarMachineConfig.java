package se.grunka.warmachine;

import java.util.HashMap;
import java.util.Map;

public class WarMachineConfig {
    public int port = 8080;
    public Map<String, String> paths = new HashMap<String, String>();
    public int threads = 200;
}
