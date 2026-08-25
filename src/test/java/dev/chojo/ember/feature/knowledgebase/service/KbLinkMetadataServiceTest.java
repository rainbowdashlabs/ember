/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.service;

import dev.chojo.ember.feature.federation.service.RemoteUrlValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class KbLinkMetadataServiceTest {
    private HttpClient httpClient;
    private RemoteUrlValidator urlValidator;
    private KbLinkMetadataService service;

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> response(int status, String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        return response;
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> redirectTo(String location) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(302);
        when(response.headers()).thenReturn(HttpHeaders.of(Map.of("location", List.of(location)), (a, b) -> true));
        return response;
    }

    /**
     * The responses are built before the stubbing starts. Mockito treats a {@code mock()} call made
     * while a stub is being written as part of that stub and fails the test.
     */
    private void respondWith(HttpResponse<String> response) throws Exception {
        when(httpClient.<String>send(any(), any())).thenReturn(response);
    }

    private void respondWith(HttpResponse<String> first, HttpResponse<String> second) throws Exception {
        when(httpClient.<String>send(any(), any())).thenReturn(first).thenReturn(second);
    }

    private void respondWith(int status, String body) throws Exception {
        respondWith(response(status, body));
    }

    @BeforeEach
    void setup() {
        httpClient = mock(HttpClient.class);
        urlValidator = mock(RemoteUrlValidator.class);
        when(urlValidator.isAllowed(any())).thenReturn(true);
        service = new KbLinkMetadataService(httpClient, urlValidator);
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
        assertNotNull(new KbLinkMetadataService(urlValidator));
    }

    /**
     * The address is the member's to choose and part of the answer is stored where they can read
     * it, so an address the validator refuses is never reached at all.
     */
    @Test
    void anAddressTheValidatorRefusesIsNotFetched() throws Exception {
        when(urlValidator.isAllowed("http://169.254.169.254/latest/meta-data")).thenReturn(false);

        var metadata = service.fetchUrlMetadata("http://169.254.169.254/latest/meta-data");

        assertNull(metadata.title());
        verify(httpClient, never()).send(any(), any());
    }

    /**
     * A public page that redirects into private space would pass a check made only at the start,
     * so every hop is checked and the walk stops where the validator says no.
     */
    @Test
    void aRedirectIntoPrivateSpaceIsNotFollowed() throws Exception {
        when(urlValidator.isAllowed("http://127.0.0.1:8080/secret")).thenReturn(false);
        respondWith(redirectTo("http://127.0.0.1:8080/secret"));

        var metadata = service.fetchUrlMetadata("https://public.example");

        assertNull(metadata.title());
        verify(httpClient, times(1)).send(any(), any());
    }

    @Test
    void aRedirectToAnotherPublicPageIsFollowed() throws Exception {
        respondWith(redirectTo("https://elsewhere.example/page"), response(200, "<title>Moved Here</title>"));

        assertEquals(
                "Moved Here", service.fetchUrlMetadata("https://public.example").title());
    }

    /**
     * A chain that never arrives is abandoned rather than walked forever.
     */
    @Test
    void aRedirectLoopEndsWithoutMetadata() throws Exception {
        respondWith(redirectTo("https://public.example/next"));

        assertNull(service.fetchUrlMetadata("https://public.example").title());
        verify(httpClient, times(4)).send(any(), any());
    }
}
