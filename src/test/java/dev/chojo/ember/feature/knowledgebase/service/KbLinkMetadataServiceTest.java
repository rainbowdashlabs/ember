/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class KbLinkMetadataServiceTest {
    private HttpClient httpClient;
    private KbLinkMetadataService service;

    @SuppressWarnings("unchecked")
    private void respondWith(int status, String body) throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        when(httpClient.<String>send(any(), any())).thenReturn(response);
    }

    @BeforeEach
    void setup() {
        httpClient = mock(HttpClient.class);
        service = new KbLinkMetadataService(httpClient);
    }

    /**
     * A page that names and describes itself hands both over, so a link entry can fill itself in.
     */
    @Test
    void aPageTitleAndDescriptionAreRead() throws Exception {
        respondWith(200, """
                <html><head><title> Example Domain </title>
                <meta name="description" content="An example page"></head><body>Hi</body></html>
                """);

        var metadata = service.fetchUrlMetadata("https://example.com");

        assertEquals("Example Domain", metadata.title());
        assertEquals("An example page", metadata.description());
    }

    /**
     * The description is read whichever way round the meta tag puts its attributes.
     */
    @Test
    void aReversedDescriptionMetaTagIsStillRead() throws Exception {
        respondWith(200, "<html><head><meta content=\"Reversed order\" name=\"description\"></head></html>");

        var metadata = service.fetchUrlMetadata("https://example.com");

        assertNull(metadata.title());
        assertEquals("Reversed order", metadata.description());
    }

    /**
     * Only the head of a page is scanned, so a title buried past the scanned window is not read and
     * a huge page cannot drag the lookup down.
     */
    @Test
    void onlyTheStartOfAPageIsScanned() throws Exception {
        respondWith(200, "x".repeat(20_000) + "<title>Too Late</title>");

        assertNull(service.fetchUrlMetadata("https://example.com").title());
    }

    @Test
    void aPageThatSaysNothingYieldsNothing() throws Exception {
        respondWith(200, "<html><body>plain</body></html>");

        var metadata = service.fetchUrlMetadata("https://example.com");

        assertNull(metadata.title());
        assertNull(metadata.description());
    }

    @Test
    void anEmptyOrRejectedPageYieldsNothing() throws Exception {
        respondWith(200, "");
        assertNull(service.fetchUrlMetadata("https://example.com").title());

        respondWith(404, "<title>Not Found</title>");
        assertNull(service.fetchUrlMetadata("https://example.com").title());
    }

    /**
     * An unreachable page yields empty metadata rather than failing the entry being created.
     */
    @Test
    void anUnreachablePageYieldsEmptyMetadata() throws Exception {
        when(httpClient.send(any(), any())).thenThrow(new IOException("connection refused"));

        var metadata = service.fetchUrlMetadata("https://unreachable.example");

        assertNotNull(metadata);
        assertNull(metadata.title());
        assertNull(metadata.description());
    }

    @Test
    void anInvalidUrlYieldsEmptyMetadata() {
        assertNull(service.fetchUrlMetadata("not-a-valid-url").title());
    }

    /**
     * A video contributes its title and channel as one searchable line.
     */
    @Test
    void aVideoTitleAndChannelBecomeOneLine() throws Exception {
        respondWith(200, "{\"title\": \"Never Gonna Give You Up\", \"author_name\": \"Rick Astley\"}");

        assertEquals("Never Gonna Give You Up Rick Astley", service.fetchYoutubeMetadata("https://youtu.be/x"));
    }

    @Test
    void aVideoWithoutTitleOrChannelYieldsABlankLine() throws Exception {
        respondWith(200, "{}");

        assertEquals(" ", service.fetchYoutubeMetadata("https://youtu.be/x"));
    }

    @Test
    void anUnknownOrUnreachableVideoYieldsNothing() throws Exception {
        respondWith(404, "not found");
        assertNull(service.fetchYoutubeMetadata("https://youtu.be/gone"));

        when(httpClient.send(any(), any())).thenThrow(new IOException("connection refused"));
        assertNull(service.fetchYoutubeMetadata("https://youtu.be/gone"));
    }

    /**
     * The default client is built without a caller supplying one, so the service is usable as an
     * injected singleton.
     */
    @Test
    void theDefaultClientIsBuiltWithoutACaller() {
        assertNotNull(new KbLinkMetadataService());
    }
}
