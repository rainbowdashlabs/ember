/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.push.service;

import dev.chojo.ember.feature.federation.service.OutboundHttp;
import dev.chojo.ember.feature.federation.service.RefusedDestinationException;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A push address that cannot be reached right now is tried again; only one that may never be
 * reached counts as gone, so a resolver that hiccups does not cost anybody their subscription.
 */
class OutboundPushTransportTest {
    private static final URI ENDPOINT = URI.create("https://push.example/abc");

    private static PushTransport.Answer answerFor(RefusedDestinationException.Reason reason) throws Exception {
        var http = mock(OutboundHttp.class);
        when(http.send(any(), any())).thenThrow(new RefusedDestinationException(reason, "refused"));
        return new OutboundPushTransport(http).post(ENDPOINT, Map.of(), new byte[0]);
    }

    @Test
    void aNameThatDoesNotResolveNowIsTriedAgain() throws Exception {
        var answer = answerFor(RefusedDestinationException.Reason.UNRESOLVABLE);

        assertFalse(answer.gone());
        assertTrue(answer.worthRetrying());
    }

    @Test
    void anAddressThatMayNeverBeReachedIsGone() throws Exception {
        assertTrue(answerFor(RefusedDestinationException.Reason.NOT_PUBLIC).gone());
        assertTrue(answerFor(RefusedDestinationException.Reason.MALFORMED).gone());
    }
}
