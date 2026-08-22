/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember;

import com.google.inject.Guice;
import dev.chojo.ember.conf.Conf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * That the application can be put together at all.
 *
 * <p>Everything else here tests what a class does once it exists. Nothing tested whether the classes can be
 * built in the first place, and they cannot always: two services that reach for each other while both are
 * still being built leave the container with nothing to hand either of them. That failure appears at start
 * up and nowhere earlier, which meant finding it by starting the application and reading why it stopped.
 *
 * <p>The event bus is where this is easiest to walk into. It is handed every handler when it is built, so a
 * handler that asks for a service built on something that publishes an event has closed a ring. Asking for
 * that service when the event arrives rather than when the handler is built opens it again.
 *
 * <p>The container is built exactly as it is at start up, because that is the only way the failure shows:
 * asked to wire everything up lazily instead, it reports no problem and the application still refuses to
 * start. Building it is all that happens; nothing is asked to do any work afterwards.
 */
class WiringTest {

    @Test
    void everythingCanBeBuilt() {
        assertDoesNotThrow(() -> Guice.createInjector(new EmberModule(new Conf())));
    }
}
