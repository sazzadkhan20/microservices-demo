package com.platform.common.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Loads repo-root {@code .env} into the Spring {@link ConfigurableEnvironment} so IDE Run/Debug
 * works without manually pasting every secret into the run configuration.
 *
 * <p>Existing OS env / system properties / CLI args win — {@code .env} only fills gaps.
 * Disable with {@code PLATFORM_DOTENV_DISABLED=true}.
 */
public class DotEnvEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    public static final String PROPERTY_SOURCE_NAME = "platformDotEnv";
    private static final String DISABLE_FLAG = "PLATFORM_DOTENV_DISABLED";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (isDisabled(environment)) {
            return;
        }
        if (environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)) {
            return;
        }
        Optional<Path> file = DotEnvLoader.findDotEnv(Path.of("").toAbsolutePath());
        if (file.isEmpty()) {
            // Also try user.dir explicitly (IDE module working directory).
            String userDir = System.getProperty("user.dir");
            if (userDir != null && !userDir.isBlank()) {
                file = DotEnvLoader.findDotEnv(Path.of(userDir));
            }
        }
        if (file.isEmpty()) {
            return;
        }
        try {
            Map<String, String> parsed = DotEnvLoader.load(file.get());
            if (parsed.isEmpty()) {
                return;
            }
            Map<String, Object> gaps = new HashMap<>();
            for (Map.Entry<String, String> e : parsed.entrySet()) {
                String key = e.getKey();
                // Prefer real process environment; ignore empty IDE placeholders.
                String fromEnv = System.getenv(key);
                if (fromEnv != null && !fromEnv.isBlank()) {
                    continue;
                }
                if (System.getProperty(key) != null && !System.getProperty(key).isBlank()) {
                    continue;
                }
                String existing = environment.getProperty(key);
                if (existing != null && !existing.isBlank()) {
                    continue;
                }
                gaps.put(key, e.getValue());
            }
            if (gaps.isEmpty()) {
                return;
            }
            // After systemEnvironment so OS env wins; before random/default sources is enough for placeholders.
            if (environment.getPropertySources().contains("systemEnvironment")) {
                environment.getPropertySources().addAfter("systemEnvironment",
                        new MapPropertySource(PROPERTY_SOURCE_NAME, gaps));
            } else {
                environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, gaps));
            }
        } catch (Exception ignored) {
            // Local convenience only — never block boot if .env is unreadable.
        }
    }

    private static boolean isDisabled(ConfigurableEnvironment environment) {
        String flag = environment.getProperty(DISABLE_FLAG, System.getenv().getOrDefault(DISABLE_FLAG, "false"));
        return "1".equals(flag) || "true".equalsIgnoreCase(flag);
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
