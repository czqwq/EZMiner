package com.czqwq.EZMiner.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FileReadUtils {

    public static final Logger LOG = LogManager.getLogger();

    public static String readText(String path) {
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        StringBuilder content = new StringBuilder();
        try (InputStream is = FileReadUtils.class.getResourceAsStream(path);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line)
                    .append(System.lineSeparator());
            }
        } catch (IOException | NullPointerException e) {
            LOG.error("Failed to read file: " + path, e);
            return "";
        }
        return content.toString();
    }
}
