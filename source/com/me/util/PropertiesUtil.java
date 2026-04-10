package com.me.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PropertiesUtil {
    private static final Logger LOGGER = Logger.getGlobal();

    public static Properties getProperties(Path path) {
        LOGGER.info("load properties: " + path);//No I18N
        Properties properties = new Properties();
        loadProperties(properties, path);
        return properties;
    }

    public static void printProperties(Path path) {
        LOGGER.info("\n".concat(getProperties(path).entrySet().stream().map(Map.Entry::toString).collect(Collectors.joining(System.lineSeparator()))));
    }

    public static void storeProperties(Properties properties, Path path) {
        LOGGER.info("store properties: " + path);//No I18N
        try (OutputStream outputStream = Files.newOutputStream(path)) {
            properties.store(outputStream, null);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void loadProperties(Properties properties, Path path) {
        try (BufferedReader bufferedReader = Files.newBufferedReader(path)) {
            properties.load(bufferedReader);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void storePropertiesWithoutFormat(Path path, Map<String, String> map) {
        LOGGER.info("Write properties: " + path);
        StringBuffer buffer = new StringBuffer();
        try (Stream<String> stream = Files.lines(path)) {
            stream.map(line -> {
                if (line.startsWith("#")) {
                    return line;
                } else if (line.contains("=")) {
                    String[] lineArr = line.split("=");
                    String key = lineArr[0];
                    String value = "";
                    if (lineArr.length >= 2) {
                        value = lineArr[1];
                    }
                    return key + "=" + map.getOrDefault(key, value);
                } else {
                    return line;
                }
            }).forEach(line -> buffer.append(line).append(System.lineSeparator()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        try (BufferedWriter writer = Files.newBufferedWriter(path)) {
            writer.append(buffer).flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
