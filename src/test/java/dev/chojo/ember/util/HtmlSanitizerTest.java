/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HtmlSanitizerTest {

    @Test
    void blankInputIsEmptyOutput() {
        assertEquals("", HtmlSanitizer.sanitize(null, HtmlSanitizer.Policy.RICH));
        assertEquals("", HtmlSanitizer.sanitize("", HtmlSanitizer.Policy.RICH));
        assertEquals("", HtmlSanitizer.sanitize("   ", HtmlSanitizer.Policy.STRICT));
    }

    @Test
    void scriptTagsAreStripped() {
        String out = HtmlSanitizer.sanitize("<p>Hi</p><script>alert(1)</script>", HtmlSanitizer.Policy.RICH);
        assertFalse(out.contains("<script"));
        assertFalse(out.contains("alert"));
        assertTrue(out.contains("Hi"));
    }

    @Test
    void onerrorAttributeIsStripped() {
        String out = HtmlSanitizer.sanitize(
                "<img src=\"/kb/images/abc\" onerror=\"alert(1)\" alt=\"x\"/>", HtmlSanitizer.Policy.RICH);
        assertFalse(out.contains("onerror"));
        assertTrue(out.contains("src=\"/kb/images/abc\""));
    }

    @Test
    void javascriptUrlInAnchorIsStripped() {
        String out = HtmlSanitizer.sanitize("<a href=\"javascript:alert(1)\">x</a>", HtmlSanitizer.Policy.RICH);
        assertFalse(out.contains("javascript:"));
    }

    @Test
    void formAndObjectTagsAreStripped() {
        String out = HtmlSanitizer.sanitize(
                "<form action=\"x\"><input/></form><object data=\"x\"></object>", HtmlSanitizer.Policy.RICH);
        assertFalse(out.contains("<form"));
        assertFalse(out.contains("<object"));
        assertFalse(out.contains("<input"));
    }

    @Test
    void crossOriginIframeIsStripped() {
        String out = HtmlSanitizer.sanitize(
                "<iframe src=\"https://evil.example.com/embed\"></iframe>", HtmlSanitizer.Policy.RICH);
        assertFalse(out.contains("<iframe"));
        assertFalse(out.contains("evil.example.com"));
    }

    @Test
    void youtubeIframeIsAllowedWithEnforcedAttributes() {
        String out = HtmlSanitizer.sanitize(
                "<iframe src=\"https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ\"></iframe>",
                HtmlSanitizer.Policy.RICH);
        assertTrue(out.contains("<iframe"));
        assertTrue(out.contains("sandbox=\"allow-scripts allow-same-origin allow-presentation\""));
        assertTrue(out.contains("loading=\"lazy\""));
        assertTrue(out.contains("referrerpolicy=\"no-referrer\""));
    }

    @Test
    void externalImageSrcIsStripped() {
        String out = HtmlSanitizer.sanitize("<img src=\"https://tracker.example/x.png\"/>", HtmlSanitizer.Policy.RICH);
        assertFalse(out.contains("tracker.example"));
    }

    @Test
    void kbImagePathIsAllowed() {
        String relativePath =
                HtmlSanitizer.sanitize("<img src=\"/kb/images/abc?size=1024\"/>", HtmlSanitizer.Policy.RICH);
        assertTrue(relativePath.contains("src=\"/kb/images/abc?size=1024\""));

        String legacyApiPath =
                HtmlSanitizer.sanitize("<img src=\"/api/v1/kb/images/abc\"/>", HtmlSanitizer.Policy.RICH);
        assertTrue(legacyApiPath.contains("src=\"/api/v1/kb/images/abc\""));
    }

    /**
     * Every picture the media browser inserts is addressed on one of the library's two routes, so a
     * body that mentions one has to survive being rendered. Left out, the sanitiser drops the image
     * silently and the author is never told the picture they inserted is gone.
     */
    @Test
    void mediaLibraryPathsAreAllowed() {
        String publicRoute = HtmlSanitizer.sanitize(
                "<img src=\"/api/v1/public/media/abc-uid/deadbeef\"/>", HtmlSanitizer.Policy.RICH);
        assertTrue(publicRoute.contains("src=\"/api/v1/public/media/abc-uid/deadbeef\""));

        String authenticatedRoute =
                HtmlSanitizer.sanitize("<img src=\"/api/v1/media/file/deadbeef\"/>", HtmlSanitizer.Policy.RICH);
        assertTrue(authenticatedRoute.contains("src=\"/api/v1/media/file/deadbeef\""));

        String withWidth = HtmlSanitizer.sanitize(
                "<img src=\"/api/v1/public/media/abc-uid/deadbeef?w=1024\"/>", HtmlSanitizer.Policy.RICH);
        assertTrue(withWidth.contains("?w=1024"));
    }

    @Test
    void publicKbImagePathIsAllowed() {
        String out = HtmlSanitizer.sanitize(
                "<img src=\"/api/v1/public/kb/abc-uid/images/img-id\"/>", HtmlSanitizer.Policy.RICH);
        assertTrue(out.contains("src=\"/api/v1/public/kb/abc-uid/images/img-id\""));
    }

    @Test
    void spanStyleAllowsOnlyColorProperties() {
        String out = HtmlSanitizer.sanitize(
                "<span style=\"color: #ff0; background-color: red; position: absolute; "
                        + "background-image: url(javascript:alert(1))\">x</span>",
                HtmlSanitizer.Policy.RICH);
        assertTrue(out.contains("color: #ff0"));
        assertTrue(out.contains("background-color: red"));
        assertFalse(out.contains("position"));
        assertFalse(out.contains("background-image"));
        assertFalse(out.contains("javascript"));
    }

    @Test
    void spanStyleRejectsExpressionValues() {
        String out = HtmlSanitizer.sanitize(
                "<span style=\"color: expression(alert(1))\">x</span>", HtmlSanitizer.Policy.RICH);
        assertFalse(out.contains("expression"));
        assertFalse(out.contains("style"));
    }

    @Test
    void styleAttributeOnOtherTagsIsStripped() {
        String out = HtmlSanitizer.sanitize("<p style=\"color:red\">hi</p>", HtmlSanitizer.Policy.RICH);
        assertFalse(out.contains("style"));
        assertTrue(out.contains("<p>hi</p>"));
    }

    @Test
    void anchorsGetSafeRelEnforced() {
        String out = HtmlSanitizer.sanitize("<a href=\"https://example.com\">x</a>", HtmlSanitizer.Policy.RICH);
        assertTrue(out.contains("rel=\"nofollow noopener noreferrer\""));
    }

    @Test
    void richPolicyKeepsTables() {
        String input = "<table><thead><tr><th>a</th></tr></thead><tbody><tr><td>b</td></tr></tbody></table>";
        String out = HtmlSanitizer.sanitize(input, HtmlSanitizer.Policy.RICH);
        assertTrue(out.contains("<table>"));
        assertTrue(out.contains("<th>a</th>"));
        assertTrue(out.contains("<td>b</td>"));
    }

    @Test
    void richPolicyKeepsHeadingsListsCodeMark() {
        String input = "<h2 id=\"x\">T</h2><ul><li>a</li></ul><pre><code>c</code></pre><mark>m</mark>";
        String out = HtmlSanitizer.sanitize(input, HtmlSanitizer.Policy.RICH);
        assertTrue(out.contains("<h2 id=\"x\">T</h2>"));
        assertTrue(out.contains("<li>a</li>"));
        assertTrue(out.contains("<code>c</code>"));
        assertTrue(out.contains("<mark>m</mark>"));
    }

    @Test
    void strictPolicyStripsImages() {
        String out = HtmlSanitizer.sanitize("<img src=\"/kb/images/abc\"/>", HtmlSanitizer.Policy.STRICT);
        assertFalse(out.contains("<img"));
    }

    @Test
    void strictPolicyStripsYoutubeIframe() {
        String out = HtmlSanitizer.sanitize(
                "<iframe src=\"https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ\"></iframe>",
                HtmlSanitizer.Policy.STRICT);
        assertFalse(out.contains("<iframe"));
    }

    @Test
    void strictPolicyKeepsParagraphsAndLinks() {
        String out = HtmlSanitizer.sanitize(
                "<p>see <a href=\"https://example.com\">site</a></p>", HtmlSanitizer.Policy.STRICT);
        assertTrue(out.contains("<p>see <a"));
        assertTrue(out.contains("href=\"https://example.com\""));
    }
}
