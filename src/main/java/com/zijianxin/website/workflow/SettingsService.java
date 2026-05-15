package com.zijianxin.website.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class SettingsService {

    private static final Path SETTINGS_PATH = Path.of("data", "app-settings.json");
    private static final Logger log = LoggerFactory.getLogger(SettingsService.class);

    private final ObjectMapper objectMapper;
    private SettingsModels.AppSettings cachedSettings;

    public SettingsService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.cachedSettings = loadSettings();
    }

    public synchronized SettingsModels.AppSettings getSettings() {
        return cachedSettings;
    }

    public synchronized SettingsModels.AiSettings saveAiSettings(SettingsModels.AiSettings settings) {
        cachedSettings = cachedSettings.withAi(settings);
        persist();
        return cachedSettings.ai();
    }

    public synchronized SettingsModels.SearchSettings saveSearchSettings(SettingsModels.SearchSettings settings) {
        cachedSettings = cachedSettings.withSearch(settings);
        persist();
        return cachedSettings.search();
    }

    public synchronized SettingsModels.CrawlerSettings saveCrawlerSettings(SettingsModels.CrawlerSettings settings) {
        cachedSettings = cachedSettings.withCrawler(settings);
        persist();
        return cachedSettings.crawler();
    }

    public synchronized SettingsModels.MailSettings saveMailSettings(SettingsModels.MailSettings settings) {
        cachedSettings = cachedSettings.withMail(settings);
        persist();
        return cachedSettings.mail();
    }

    public synchronized SettingsModels.GeneralSettings saveGeneralSettings(SettingsModels.GeneralSettings settings) {
        cachedSettings = cachedSettings.withGeneral(settings);
        persist();
        return cachedSettings.general();
    }

    private SettingsModels.AppSettings loadSettings() {
        if (!Files.exists(SETTINGS_PATH)) {
            SettingsModels.AppSettings defaults = SettingsModels.AppSettings.defaults();
            writeSettings(defaults);
            log.info("Settings file not found. Created default settings at {}", SETTINGS_PATH.toAbsolutePath());
            return defaults;
        }

        try (InputStream inputStream = Files.newInputStream(SETTINGS_PATH)) {
            SettingsModels.AppSettings loaded = objectMapper.readValue(inputStream, SettingsModels.AppSettings.class);
            return loaded == null ? SettingsModels.AppSettings.defaults() : loaded;
        } catch (IOException exception) {
            SettingsModels.AppSettings defaults = SettingsModels.AppSettings.defaults();
            log.warn("Failed to parse settings file {}. Rewriting with defaults. Cause: {}", SETTINGS_PATH.toAbsolutePath(), exception.getMessage());
            writeSettings(defaults);
            return defaults;
        }
    }

    private void persist() {
        writeSettings(cachedSettings);
    }

    private void writeSettings(SettingsModels.AppSettings settings) {
        try {
            Path parent = SETTINGS_PATH.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (OutputStream outputStream = Files.newOutputStream(SETTINGS_PATH)) {
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(outputStream, settings);
            }
        } catch (IOException exception) {
            log.error("Failed to persist application settings to {}", SETTINGS_PATH.toAbsolutePath(), exception);
            throw new IllegalStateException("Failed to persist application settings", exception);
        }
    }
}
