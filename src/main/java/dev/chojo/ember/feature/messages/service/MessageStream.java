/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.service;

/**
 * One open stream of message signals to one browser tab.
 */
public interface MessageStream {

    /**
     * Sends one named event.
     *
     * @param event the event's name
     * @param data  what it carries, written as JSON
     * @return false once the tab is gone, which takes the stream out of the hub
     */
    boolean send(String event, Object data);
}
