package com.platform.common.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DotEnvLoaderTest {

    @TempDir
    Path temp;

    @Test
    void parsesKeysIgnoresCommentsAndQuotes() throws Exception {
        Path env = temp.resolve(".env");
        Files.writeString(env, """
                # comment
                PLATFORM_SERVICE_KEY=local-service-key-change-me
                export JWT_SECRET="quoted-secret"
                EMPTY=
                bad line
                QUOTED='x'
                """, StandardCharsets.UTF_8);

        Map<String, String> map = DotEnvLoader.load(env);
        assertEquals("local-service-key-change-me", map.get("PLATFORM_SERVICE_KEY"));
        assertEquals("quoted-secret", map.get("JWT_SECRET"));
        assertEquals("", map.get("EMPTY"));
        assertEquals("x", map.get("QUOTED"));
    }

    @Test
    void findsDotEnvWalkingParents() throws Exception {
        Path root = temp.resolve("repo");
        Path module = root.resolve("report-service");
        Files.createDirectories(module);
        Files.writeString(root.resolve(".env"), "PLATFORM_SERVICE_KEY=from-root\n", StandardCharsets.UTF_8);

        Optional<Path> found = DotEnvLoader.findDotEnv(module);
        assertTrue(found.isPresent());
        assertEquals("from-root", DotEnvLoader.load(found.get()).get("PLATFORM_SERVICE_KEY"));
    }
}
