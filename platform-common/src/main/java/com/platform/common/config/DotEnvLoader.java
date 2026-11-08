package com.platform.common.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Parses a repo-root {@code .env} (KEY=VALUE) for local IDE / bootRun startups.
 * Does not override process environment or system properties — callers decide merge policy.
 */
public final class DotEnvLoader {

    private DotEnvLoader() {
    }

    /**
     * Walks {@code start} and its parents (up to 8 levels) looking for a readable {@code .env}.
     */
    public static Optional<Path> findDotEnv(Path start) {
        Path dir = start == null ? Path.of(".").toAbsolutePath().normalize() : start.toAbsolutePath().normalize();
        if (Files.isRegularFile(dir)) {
            dir = dir.getParent();
        }
        for (int i = 0; i < 8 && dir != null; i++) {
            Path candidate = dir.resolve(".env");
            if (Files.isRegularFile(candidate) && Files.isReadable(candidate)) {
                return Optional.of(candidate);
            }
            dir = dir.getParent();
        }
        return Optional.empty();
    }

    public static Map<String, String> load(Path file) throws IOException {
        if (file == null || !Files.isRegularFile(file)) {
            return Map.of();
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("export ")) {
                line = line.substring("export ".length()).trim();
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = line.substring(0, eq).trim();
            if (key.isEmpty() || !isEnvKey(key)) {
                continue;
            }
            String value = stripQuotes(line.substring(eq + 1).trim());
            out.putIfAbsent(key, value);
        }
        return Collections.unmodifiableMap(out);
    }

    private static boolean isEnvKey(String key) {
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (!(c == '_' || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9'))) {
                return false;
            }
        }
        return true;
    }

    private static String stripQuotes(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }
}
