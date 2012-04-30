package se.grunka.warmachine;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.TTCCLayout;
import org.eclipse.jetty.server.AbstractConnector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.util.thread.ExecutorThreadPool;
import org.eclipse.jetty.webapp.WebAppContext;
import org.eclipse.jetty.webapp.WebInfConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class WarMachine {
    private static final String USAGE = "META-INF/war-machine/usage.txt";
    private static final String CONFIG = "META-INF/war-machine/config.json";
    private static final String WAR_LOCATION = "META-INF/war-machine/war/";
    private static final String RESOURCE = "resource:";
    private static final String TEMPORARY_DIRECTORY = "java.io.tmpdir";
    private final WarMachineConfig config;

    public WarMachine(WarMachineConfig config) {
        this.config = config;
    }

    public static void main(String[] args) throws IOException {
        WarMachineConfig config = readConfig();
        boolean createPackage = false;
        String packageFile = null;

        Pattern pathPattern = Pattern.compile("^(.*?)=(.*)$");
        Matcher matcher;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("-p".equals(arg) || "--port".equals(arg)) {
                try {
                    config.port = Integer.valueOf(args[++i]);
                } catch (NumberFormatException e) {
                    System.err.println("Invalid value for port number " + args[i]);
                    System.exit(1);
                }
            } else if ("-t".endsWith(arg) || "--threads".equals(arg)) {
                try {
                    config.threads = Integer.parseInt(args[++i]);
                } catch (NumberFormatException e) {
                    System.err.println("Invalid valid for number of threads " + args[i]);
                    System.exit(1);
                }
            } else if ("--package".equals(arg)) {
                createPackage = true;
                packageFile = args[++i];
            } else if ((matcher = pathPattern.matcher(arg)).matches()) {
                String fileName = matcher.group(2);
                boolean fileExists = new File(fileName).exists();
                if (!fileExists) {
                    System.err.println("War-file " + fileName + " was not found");
                    System.exit(1);
                }
                config.paths.put(matcher.group(1), fileName);
            } else {
                System.err.println("Unrecognized argument " + arg);
                InputStream input = ClassLoader.getSystemClassLoader().getResourceAsStream(USAGE);
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                copy(input, output);
                System.err.println(output.toString());
                System.exit(1);
            }
        }

        extractPackagedWars(config);

        if (createPackage) {
            createPackage(config, packageFile);
        } else {
            new WarMachine(config).runForever();
        }
    }

    private static WarMachineConfig readConfig() {
        return gson().fromJson(new InputStreamReader(ClassLoader.getSystemClassLoader().getResourceAsStream(CONFIG)), WarMachineConfig.class);
    }

    private static void createPackage(WarMachineConfig config, String packageFile) throws IOException {
        String jarFile = WarMachine.class.getProtectionDomain().getCodeSource().getLocation().getFile();
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

    private static void addConfig(WarMachineConfig config, ZipOutputStream zipOutputStream) throws IOException {
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
        return new GsonBuilder().setPrettyPrinting().create();
    }

    private static void addWars(WarMachineConfig config, ZipOutputStream zipOutputStream) throws IOException {
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

    private static void extractPackagedWars(WarMachineConfig config) throws IOException {
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

    private Handler createWebAppsHandler(Map<String, String> paths, Logger logger) {
        List<Handler> webApps = new ArrayList<Handler>();
        for (Map.Entry<String, String> path : paths.entrySet()) {
            String fileName = path.getValue();
            String contextPath = path.getKey();
            webApps.add(new WebAppContext(fileName, contextPath));
            logger.info("Mapping " + fileName + " to " + contextPath);
        }
        webApps.add(new DefaultHandler());
        ContextHandlerCollection contexts = new ContextHandlerCollection();
        contexts.setHandlers(webApps.toArray(new Handler[webApps.size()]));
        return contexts;
    }

    public void runForever() {
        configureLogger();

        final Logger logger = LoggerFactory.getLogger(getClass());

        if (config.paths.size() == 0) {
            logger.warn("No web apps specified, will only serve 404s");
        }

        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                logger.error("Uncaught exception in thread " + String.valueOf(t), e);
            }
        });

        logger.info("Will listen on port " + config.port);
        logger.info("Will use " + config.threads + " threads");
        Server jetty = new Server(config.port);
        jetty.setHandler(createWebAppsHandler(config.paths, logger));
        final ExecutorService executor = Executors.newFixedThreadPool(config.threads);
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                executor.shutdown();
            }
        });
        jetty.setThreadPool(new ExecutorThreadPool(executor));
        /*
        //TODO should you be able to configure the timeout? / set it to a good default value or is it already?
        for (Connector connector : jetty.getConnectors()) {
            connector.setMaxIdleTime(config.timeout);
        }
        */
        try {
            logger.info("Initializing");
            jetty.start();
            logger.info("Running");
            jetty.join();
        } catch (Exception e) {
            logger.error("Failure while running server", e);
            System.exit(1);
        }
    }

    private void configureLogger() {
        org.apache.log4j.Logger rootLogger = org.apache.log4j.Logger.getRootLogger();
        rootLogger.setLevel(Level.INFO);
        TTCCLayout layout = new TTCCLayout("ISO8601");
        rootLogger.addAppender(new ConsoleAppender(layout, ConsoleAppender.SYSTEM_OUT));
        org.apache.log4j.Logger.getLogger(Server.class).setLevel(Level.WARN);
        org.apache.log4j.Logger.getLogger(AbstractConnector.class).setLevel(Level.WARN);
        org.apache.log4j.Logger.getLogger(ContextHandler.class).setLevel(Level.WARN);
        org.apache.log4j.Logger.getLogger(WebInfConfiguration.class).setLevel(Level.WARN);
    }
}
