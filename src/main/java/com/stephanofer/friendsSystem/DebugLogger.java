package com.stephanofer.friendsSystem;

import java.util.Map;
import org.slf4j.Logger;

public final class DebugLogger {

    private final Logger logger;
    private final PluginConfig.Debug config;

    public DebugLogger(Logger logger, PluginConfig.Debug config) {
        this.logger = logger;
        this.config = config;
    }

    public void presence(String event, Map<String, String> fields) {
        log(this.config.presence(), event, fields);
    }

    public void settings(String event, Map<String, String> fields) {
        log(this.config.settings(), event, fields);
    }

    public void notifications(String event, Map<String, String> fields) {
        log(this.config.notifications(), event, fields);
    }

    public void broadcasts(String event, Map<String, String> fields) {
        log(this.config.broadcasts(), event, fields);
    }

    public void prefixes(String event, Map<String, String> fields) {
        log(this.config.prefixes(), event, fields);
    }

    public void friendList(String event, Map<String, String> fields) {
        log(this.config.friendList(), event, fields);
    }

    public void identity(String event, Map<String, String> fields) {
        log(this.config.identity(), event, fields);
    }

    private void log(boolean categoryEnabled, String event, Map<String, String> fields) {
        if (!this.config.enabled() || !categoryEnabled) {
            return;
        }
        StringBuilder builder = new StringBuilder("[FriendsSystem Debug] ").append(event);
        fields.forEach((key, value) -> builder.append(System.lineSeparator()).append("  ").append(key).append(": ").append(value));
        this.logger.info(builder.toString());
    }
}
