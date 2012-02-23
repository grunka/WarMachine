package se.grunka.basal;

import ch.qos.logback.classic.Level;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.webapp.WebAppContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class Basal {
    private static final Logger LOG = LoggerFactory.getLogger(Basal.class);
    private static final String USAGE = "META-INF/basal/usage.txt";
    private static final String CONFIG = "META-INF/basal/config.json";
    private static final String WAR_LOCATION = "META-INF/basal/war/";
    private static final String RESOURCE = "resource:";
    private static final String TEMPORARY_DIRECTORY = "java.io.tmpdir";
    private final BasalConfig config;

    public Basal(BasalConfig config) {
        this.config = config;
    }

    public static void main(String[] args) throws IOException {
        BasalConfig config = readConfig();
        boolean createPackage = false;
        String packageFile = null;

        Pattern pathPattern = Pattern.compile("^(.*?)=(.*)$");
        Matcher matcher;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("-p".equals(arg)) {
                config.port = Integer.valueOf(args[++i]);
                //TODO check that it is a valid number
            } else if ("-l".equals(arg)) {
                Level level = Level.toLevel(args[++i], null);
                if (level == null) {
                    LOG.error("Invalid log level " + args[i]);
                    System.exit(1);
                }
                config.logLevel = level;
            } else if ("--package".equals(arg)) {
                createPackage = true;
                packageFile = args[++i];
            } else if ((matcher = pathPattern.matcher(arg)).matches()) {
                config.paths.put(matcher.group(1), matcher.group(2));
                //TODO check that file exists
            } else {
                LOG.error("Unrecognized argument " + arg);
                InputStream input = ClassLoader.getSystemClassLoader().getResourceAsStream(USAGE);
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                copy(input, output);
                LOG.error(output.toString());
                System.exit(1);
            }
        }

        extractPackagedWars(config);

        if (createPackage) {
            createPackage(config, packageFile);
        } else {
            new Basal(config).runForever();
        }
    }

    private static BasalConfig readConfig() {
        return gson().fromJson(new InputStreamReader(ClassLoader.getSystemClassLoader().getResourceAsStream(CONFIG)), BasalConfig.class);
    }

    private static void createPackage(BasalConfig config, String packageFile) throws IOException {
        String jarFile = Basal.class.getProtectionDomain().getCodeSource().getLocation().getFile();
        ZipInputStream zipInputStream = new ZipInputStream(new FileInputStream(jarFile));
        try {
            ZipOutputStream zipOutputStream = new ZipOutputStream(new FileOutputStream(packageFile));
            try {
                addWars(config, zipOutputStream);
                addConfig(config, zipOutputStream);
                ZipEntry entry;
                while ((entry = zipInputStream.getNextEntry()) != null) {
                    ZipEntry copy = new ZipEntry(entry);
                    if (!copy.getName().startsWith(WAR_LOCATION) && !CONFIG.equals(copy.getName())) {
                        zipOutputStream.putNextEntry(copy);
                        copy(zipInputStream, zipOutputStream);
                        zipOutputStream.closeEntry();
                    }
                }
            } finally {
                zipOutputStream.close();
            }
        } finally {
            zipInputStream.close();
        }
    }

    private static void addConfig(BasalConfig config, ZipOutputStream zipOutputStream) throws IOException {
        for (Map.Entry<String, String> pathEntry : config.paths.entrySet()) {
            File warFile = new File(pathEntry.getValue());
            pathEntry.setValue(RESOURCE + warFile.getName());
        }
        ZipEntry configEntry = new ZipEntry(CONFIG);
        zipOutputStream.putNextEntry(configEntry);
        zipOutputStream.write(toJson(config).getBytes());
        zipOutputStream.closeEntry();
    }

    private static String toJson(Object value) {
        return gson().toJson(value);
    }

    private static Gson gson() {
        return new GsonBuilder().registerTypeAdapter(Level.class, new LevelTypeAdapter()).setPrettyPrinting().create();
    }

    private static void addWars(BasalConfig config, ZipOutputStream zipOutputStream) throws IOException {
        for (String location : config.paths.values()) {
            File currentLocation = new File(location);
            ZipEntry zipEntry = new ZipEntry(WAR_LOCATION + currentLocation.getName());
            FileInputStream inputStream = new FileInputStream(currentLocation);
            try {
                zipOutputStream.putNextEntry(zipEntry);
                copy(inputStream, zipOutputStream);
                zipOutputStream.closeEntry();
            } finally {
                inputStream.close();
            }
        }
    }

    private static void extractPackagedWars(BasalConfig config) throws IOException {
        File temporaryDirectory = new File(System.getProperty(TEMPORARY_DIRECTORY));
        for (Map.Entry<String, String> pathEntry : config.paths.entrySet()) {
            String currentLocation = pathEntry.getValue();
            if (currentLocation.startsWith(RESOURCE)) {
                String resource = WAR_LOCATION + currentLocation.substring(RESOURCE.length());
                InputStream resourceStream = ClassLoader.getSystemClassLoader().getResourceAsStream(resource);
                try {
                    File temporaryLocation = new File(temporaryDirectory, new File(resource).getName());
                    FileOutputStream outputStream = new FileOutputStream(temporaryLocation, false);
                    try {
                        copy(resourceStream, outputStream);
                        pathEntry.setValue(temporaryLocation.getPath());
                    } finally {
                        outputStream.close();
                    }
                } finally {
                    resourceStream.close();
                }
            }
        }
    }

    private static void copy(InputStream from, OutputStream to) throws IOException {
        byte[] buffer = new byte[4096];
        int bytes;
        while ((bytes = from.read(buffer)) != -1) {
            to.write(buffer, 0, bytes);
        }
    }

    private Handler createWebAppsHandler(Map<String, String> paths) {
        List<Handler> webApps = new ArrayList<Handler>();
        for (Map.Entry<String, String> path : paths.entrySet()) {
            String fileName = path.getValue();
            webApps.add(new WebAppContext(fileName, path.getKey()));
        }
        webApps.add(new DefaultHandler());
        ContextHandlerCollection contexts = new ContextHandlerCollection();
        contexts.setHandlers(webApps.toArray(new Handler[webApps.size()]));
        return contexts;
    }

    public void runForever() {
        if (config.paths.size() == 0) {
            LOG.warn("No web apps specified, will only serve 404s");
        }

        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                LOG.error("Uncaught exception in thread " + String.valueOf(t), e);
            }
        });

        configureLogger();

        Server jetty = new Server(config.port);
        jetty.setHandler(createWebAppsHandler(config.paths));
        try {
            jetty.start();
            jetty.join();
        } catch (Exception e) {
            LOG.error("Failure while running server", e);
            System.exit(1);
        }
    }

    private void configureLogger() {
        ch.qos.logback.classic.Logger rootLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        /*
        Iterator<Appender<ILoggingEvent>> appenderIterator = rootLogger.iteratorForAppenders();
        while(appenderIterator.hasNext()) {
            Appender<ILoggingEvent> appender = appenderIterator.next();
            if (appender instanceof OutputStreamAppender) {
                ((OutputStreamAppender) appender).getEncoder();
            }
        }
        */
        rootLogger.setLevel(config.logLevel);
    }
}
