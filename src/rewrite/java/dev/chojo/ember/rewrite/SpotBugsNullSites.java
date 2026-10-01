/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.rewrite;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 * The places a SpotBugs report says a null arrives where nothing allows it: a method that returns
 * null, or a parameter a caller passes null for.
 *
 * <p>Read from the XML report {@code be-spotbugs} writes. A return is reported on the method that
 * returns null. A parameter is reported on the caller, naming the method called and the 1-based
 * position of the argument, so the site is the called method's parameter at that position.
 */
final class SpotBugsNullSites {

    private static final Set<String> RETURNS = Set.of("NP_NONNULL_RETURN_VIOLATION");

    private static final Set<String> PARAMETERS = Set.of(
            "NP_NONNULL_PARAM_VIOLATION",
            "NP_NULL_PARAM_DEREF",
            "NP_NULL_PARAM_DEREF_NONVIRTUAL",
            "NP_NULL_PARAM_DEREF_ALL_TARGETS_DANGEROUS");

    private static final Set<String> NULL_ARGUMENTS = Set.of("INT_NULL_ARG", "INT_MAYBE_NULL_ARG");

    /**
     * One place that has to allow null.
     *
     * @param type       the declaring type, as SpotBugs writes it ({@code $} before a nested type)
     * @param method     the method name, {@code <init>} for a constructor
     * @param parameters the parameter part of the JVM descriptor, without the parentheses
     * @param position   0 for the return value, otherwise the 1-based parameter position
     */
    record Site(String type, String method, String parameters, int position) {

        /**
         * Whether this site is the return value.
         *
         * @return true for a return, false for a parameter
         */
        boolean isReturn() {
            return position == 0;
        }
    }

    private SpotBugsNullSites() {}

    /**
     * Reads the sites of a report.
     *
     * @param report the XML report
     * @return every distinct site the report names
     * @throws IllegalStateException when the report cannot be read
     */
    static Set<Site> read(Path report) {
        Document document = parse(report);
        Set<Site> sites = new HashSet<>();
        NodeList bugs = document.getElementsByTagName("BugInstance");
        for (int i = 0; i < bugs.getLength(); i++) {
            Element bug = (Element) bugs.item(i);
            String kind = bug.getAttribute("type");
            if (RETURNS.contains(kind)) {
                primaryMethod(bug).ifPresent(method -> sites.add(siteOf(method, 0)));
            } else if (PARAMETERS.contains(kind)) {
                calledMethod(bug).ifPresent(method -> nullArguments(bug)
                        .forEach(position -> sites.add(siteOf(method, position))));
            }
        }
        return sites;
    }

    private static Site siteOf(Element method, int position) {
        String signature = method.getAttribute("signature");
        String parameters = signature.substring(signature.indexOf('(') + 1, signature.indexOf(')'));
        return new Site(method.getAttribute("classname"), method.getAttribute("name"), parameters, position);
    }

    private static Optional<Element> primaryMethod(Element bug) {
        return children(bug, "Method").stream()
                .filter(method -> !method.hasAttribute("role"))
                .findFirst();
    }

    private static Optional<Element> calledMethod(Element bug) {
        return children(bug, "Method").stream()
                .filter(method -> "METHOD_CALLED".equals(method.getAttribute("role")))
                .findFirst();
    }

    private static List<Integer> nullArguments(Element bug) {
        return children(bug, "Int").stream()
                .filter(value -> NULL_ARGUMENTS.contains(value.getAttribute("role")))
                .map(value -> Integer.parseInt(value.getAttribute("value")))
                .toList();
    }

    private static List<Element> children(Element parent, String tag) {
        List<Element> found = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i) instanceof Element element && tag.equals(element.getTagName())) {
                found.add(element);
            }
        }
        return found;
    }

    private static Document parse(Path report) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            try (var in = Files.newInputStream(report)) {
                return factory.newDocumentBuilder().parse(in);
            }
        } catch (IOException | ParserConfigurationException | SAXException e) {
            throw new IllegalStateException("Could not read the SpotBugs report " + report, e);
        }
    }
}
