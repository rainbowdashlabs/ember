/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;

import java.util.regex.Pattern;

/**
 * Sanitises rendered HTML to a curated allow-list before it is returned to a
 * browser. The renderers (KB markdown, page markdown, legal documents) feed
 * their output through this utility so any user-authored HTML - including
 * HTML embedded inside markdown - is stripped of script vectors before
 * reaching another user's session.
 *
 * <p>Two policies are exposed:
 * <ul>
 *   <li>{@link Policy#RICH} - used by knowledge-base and station-page
 *       renderers. Permits the formatting features the in-app editor can
 *       emit (headings, lists, tables, links, KB images, YouTube embeds,
 *       coloured spans / highlights).</li>
 *   <li>{@link Policy#STRICT} - used by the legal document renderer.
 *       Same as RICH minus iframes and images, so an admin-authored legal
 *       document cannot embed remote frames or images even by accident.</li>
 * </ul>
 *
 * <p>Both policies forbid {@code javascript:} / {@code data:} URLs, strip
 * the {@code style} attribute on every element except {@code <span>} (and
 * even there only allow {@code color} / {@code background-color}), restrict
 * {@code <a href>} to {@code http} / {@code https} / {@code mailto}, and
 * limit {@code <img src>} / {@code <iframe src>} to a tiny allow-list of
 * known-safe sources (relative KB / page paths for images, YouTube embed
 * URLs for iframes).
 */
public final class HtmlSanitizer {

    private static final Pattern KB_IMAGE_PATH = Pattern.compile("^/(api/v1/)?(public/)?kb/images/.+");
    private static final Pattern KB_PUBLIC_IMAGE = Pattern.compile("^/api/v1/public/kb/[^/]+/images/.+");
    private static final Pattern PAGE_FILE_PATH = Pattern.compile("^/(api/v1/)?(public/)?pages/files/.+");
    private static final Pattern YOUTUBE_EMBED =
            Pattern.compile("^https://www\\.youtube(-nocookie)?\\.com/embed/[A-Za-z0-9_-]{11}(\\?[^\"<>]*)?$");
    private static final Pattern HEX_OR_NAMED_COLOR = Pattern.compile("^(#[0-9a-fA-F]{3,8}|[a-zA-Z]{1,40})$");

    private static final Safelist RICH = buildRich();
    private static final Safelist STRICT = buildStrict();

    private HtmlSanitizer() {}

    /**
     * Returns {@code html} stripped to the safelist selected by {@code policy}.
     * Returns the empty string when {@code html} is {@code null} or blank.
     */
    public static String sanitize(String html, Policy policy) {
        if (html == null || html.isBlank()) return "";
        Safelist safelist = policy == Policy.STRICT ? STRICT : RICH;
        String cleaned = Jsoup.clean(html, "", safelist);
        return applyDomLevelPolicies(cleaned);
    }

    /**
     * Final DOM pass run after the jsoup safelist. Trims attributes that
     * survive as syntactically valid but semantically unsafe ({@code style}
     * with non-color CSS) and drops orphan tags whose {@code src} was
     * rejected at the safelist stage ({@code <iframe>} without an allowed
     * embed URL would otherwise remain in the markup as an empty element).
     */
    private static String applyDomLevelPolicies(String html) {
        Document doc = Jsoup.parseBodyFragment(html);
        for (Element span : doc.select("span[style]")) {
            String allowed = filterSpanStyle(span.attr("style"));
            if (allowed.isEmpty()) {
                span.removeAttr("style");
            } else {
                span.attr("style", allowed);
            }
        }
        for (Element iframe : doc.select("iframe")) {
            String src = iframe.attr("src");
            if (!YOUTUBE_EMBED.matcher(src).matches()) {
                iframe.remove();
            }
        }
        for (Element img : doc.select("img")) {
            String src = img.attr("src");
            if (src.isBlank()) {
                img.remove();
            }
        }
        return doc.body().html();
    }

    private static String filterSpanStyle(String style) {
        StringBuilder out = new StringBuilder();
        for (String declaration : style.split(";")) {
            int colon = declaration.indexOf(':');
            if (colon < 0) continue;
            String property = declaration.substring(0, colon).trim().toLowerCase();
            String value = declaration.substring(colon + 1).trim();
            if (!property.equals("color") && !property.equals("background-color")) continue;
            if (!HEX_OR_NAMED_COLOR.matcher(value).matches()) continue;
            if (!out.isEmpty()) out.append("; ");
            out.append(property).append(": ").append(value);
        }
        return out.toString();
    }

    private static Safelist buildRich() {
        Safelist safelist = baseTextFormatting();
        safelist.addTags("img", "iframe", "table", "thead", "tbody", "tr", "th", "td");
        safelist.addAttributes("img", "src", "alt", "title", "width", "height", "style");
        safelist.addAttributes("iframe", "src", "title", "width", "height", "frameborder", "allowfullscreen");
        safelist.addAttributes("th", "scope", "colspan", "rowspan");
        safelist.addAttributes("td", "colspan", "rowspan");
        safelist.addProtocols("img", "src", "http", "https");
        safelist.addProtocols("iframe", "src", "https");
        safelist.addEnforcedAttribute("iframe", "sandbox", "allow-scripts allow-same-origin allow-presentation");
        safelist.addEnforcedAttribute("iframe", "loading", "lazy");
        safelist.addEnforcedAttribute("iframe", "referrerpolicy", "no-referrer");
        return new ConstrainedSafelist(safelist);
    }

    private static Safelist buildStrict() {
        return new ConstrainedSafelist(baseTextFormatting());
    }

    private static Safelist baseTextFormatting() {
        return new Safelist()
                .addTags(
                        "h1",
                        "h2",
                        "h3",
                        "h4",
                        "h5",
                        "h6",
                        "p",
                        "br",
                        "hr",
                        "ul",
                        "ol",
                        "li",
                        "blockquote",
                        "pre",
                        "code",
                        "strong",
                        "em",
                        "del",
                        "u",
                        "s",
                        "mark",
                        "sub",
                        "sup",
                        "a",
                        "span",
                        "div")
                .addAttributes("a", "href", "title", "rel")
                .addAttributes("span", "style")
                .addAttributes(":all", "id")
                .addProtocols("a", "href", "http", "https", "mailto")
                .addEnforcedAttribute("a", "rel", "nofollow noopener noreferrer");
    }

    /**
     * Policy selector for {@link HtmlSanitizer#sanitize(String, Policy)}.
     */
    public enum Policy {
        /**
         * Allows images, tables, and YouTube embeds - for KB / page content.
         */
        RICH,
        /**
         * Forbids images and iframes - for admin-authored legal documents.
         */
        STRICT
    }

    /**
     * Wraps a {@link Safelist} so {@code img[src]} and {@code iframe[src]}
     * values are checked against the curated path/URL allow-lists.
     */
    private static final class ConstrainedSafelist extends Safelist {

        private ConstrainedSafelist(Safelist delegate) {
            super(delegate);
        }

        @Override
        public boolean isSafeAttribute(String tagName, Element el, Attribute attr) {
            String name = attr.getKey().toLowerCase();
            String value = attr.getValue();
            if (tagName.equals("img") && name.equals("src")) {
                return isAllowedImageSrc(value);
            }
            if (tagName.equals("iframe") && name.equals("src")) {
                return YOUTUBE_EMBED.matcher(value).matches();
            }
            return super.isSafeAttribute(tagName, el, attr);
        }

        private boolean isAllowedImageSrc(String value) {
            if (value == null || value.isBlank()) return false;
            if (KB_IMAGE_PATH.matcher(value).matches()) return true;
            if (KB_PUBLIC_IMAGE.matcher(value).matches()) return true;
            return PAGE_FILE_PATH.matcher(value).matches();
        }
    }
}
