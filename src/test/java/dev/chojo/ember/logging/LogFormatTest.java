/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.logging;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.JsonEncoder;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.encoder.LayoutWrappingEncoder;
import ch.qos.logback.core.joran.spi.JoranException;
import org.junit.jupiter.api.Test;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The console writes readable lines unless the operator asks for JSON, which a log collector reads one
 * event at a time, stack trace included.
 */
class LogFormatTest {

    private static LoggerContext configured(String format) throws JoranException {
        var context = new LoggerContext();
        if (format != null) context.putProperty("LOG_FORMAT", format);
        var configurator = new JoranConfigurator();
        configurator.setContext(context);
        configurator.doConfigure(Objects.requireNonNull(LogFormatTest.class.getResource("/logback.xml")));
        return context;
    }

    @Test
    void theConsoleIsReadableByDefault() throws JoranException {
        var context = configured(null);
        try {
            var root = context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
            var console = (ConsoleAppender<ILoggingEvent>) root.getAppender("STDOUT_text");
            assertNotNull(console);
            assertInstanceOf(LayoutWrappingEncoder.class, console.getEncoder());
            assertNull(root.getAppender("STDOUT_json"));
        } finally {
            context.stop();
        }
    }

    @Test
    void theConsoleWritesJsonWhenAskedTo() throws JoranException {
        var context = configured("json");
        try {
            var root = context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
            var console = (ConsoleAppender<ILoggingEvent>) root.getAppender("STDOUT_json");
            assertNotNull(console);
            assertInstanceOf(JsonEncoder.class, console.getEncoder());
            assertNull(root.getAppender("STDOUT_text"));
        } finally {
            context.stop();
        }
    }
}
