/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service.wordform;

import org.jspecify.annotations.Nullable;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.List;

/**
 * Reading the elements of a Word document by their namespace and name, the way the parts of a form are
 * looked up.
 */
final class WordXml {
    /** The namespace of the main part of a Word document. */
    static final String W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";

    /** The namespace Word 2010 added, which holds the check box content control. */
    static final String W14 = "http://schemas.microsoft.com/office/word/2010/wordml";

    private WordXml() {}

    /**
     * @return the first child of that namespace and name, or null where the parent is null or has none
     */
    static @Nullable Element first(@Nullable Element parent, String namespace, String name) {
        if (parent == null) return null;
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element
                    && namespace.equals(element.getNamespaceURI())
                    && name.equals(element.getLocalName())) return element;
        }
        return null;
    }

    /** @return every child of that namespace and name, in document order */
    static List<Element> children(Element parent, String namespace, String name) {
        var out = new ArrayList<Element>();
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element
                    && namespace.equals(element.getNamespaceURI())
                    && name.equals(element.getLocalName())) out.add(element);
        }
        return out;
    }

    /** @return the attribute of that name in the main namespace, empty where the element or attribute is missing */
    static String attribute(@Nullable Element element, String name) {
        return attribute(element, W, name);
    }

    /** @return the attribute of that namespace and name, empty where the element or attribute is missing */
    static String attribute(@Nullable Element element, String namespace, String name) {
        return element == null ? "" : element.getAttributeNS(namespace, name);
    }
}
