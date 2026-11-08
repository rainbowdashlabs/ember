/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service.wordform;

import dev.chojo.ember.feature.generator.service.wordform.FormSpot.FormSpotKind;
import org.jspecify.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static dev.chojo.ember.feature.generator.service.wordform.WordXml.W;
import static dev.chojo.ember.feature.generator.service.wordform.WordXml.W14;
import static dev.chojo.ember.feature.generator.service.wordform.WordXml.attribute;
import static dev.chojo.ember.feature.generator.service.wordform.WordXml.children;
import static dev.chojo.ember.feature.generator.service.wordform.WordXml.first;

/**
 * Finds the places of a Word form that are filled in and makes LibreOffice export each of them as a form
 * field of its own.
 *
 * <p>A form marks its places in one of two ways. The old one is a form field ({@code FORMTEXT},
 * {@code FORMCHECKBOX}, {@code FORMDROPDOWN}), which LibreOffice draws but leaves out of the fields of the
 * PDF it writes. The newer one is a content control: a text, a date, a check box, a list. Every place of
 * either kind is rewritten into a content control of its own, named {@code ember-f<n>} in the order the
 * document holds them, which is how its form field in the PDF is found again. What a place held is kept
 * as its preset and cleared from the page, so the PDF shows an empty form.
 *
 * <p>Each place is read with the words that stand for it: the text before it on its line, for a check
 * box the text after it, for a place on a line of places alone the words on the line below in the same
 * order, and for a place alone in a table cell the heading of its column. A text box is read once,
 * though Word keeps a second copy of it for older programs.
 *
 * <p>Each place is also read for the font it is set in, after the sizes of scaled families are written
 * ({@link FormFontSizes}) and before any family is replaced ({@link FormFonts}), so a field laid over it
 * can print as large as the form sets it, under the name the form gives its family.
 */
final class WordFormScanner {
    private static final String MC = "http://schemas.openxmlformats.org/markup-compatibility/2006";

    /** What an emptied text place holds, wide enough for LibreOffice to give its field some room. */
    private static final String BLANK = " ".repeat(10);

    /** What an empty check box shows. */
    private static final String BOX = "☐";

    /** What a line break inside a paragraph is read as. */
    private static final char LINE_BREAK = '\n';

    /** The longest label kept. */
    private static final int MAX_LABEL = 120;

    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final Pattern COLUMNS = Pattern.compile("\\t+|\\s{3,}");

    /** Content controls that hold something other than a place to fill in. */
    private static final Set<String> NOT_FILLED_IN =
            Set.of("docPartObj", "citation", "bibliography", "equation", "picture", "group");

    private final Document body;
    private final RunFonts runFonts;
    private final List<Element> paragraphs;
    private final List<FormSpot> spots = new ArrayList<>();
    private final Map<String, TextFont> placeFonts = new HashMap<>();
    private final List<Runnable> rewrites = new ArrayList<>();
    private int counted;

    private WordFormScanner(Document body, RunFonts runFonts) {
        this.body = body;
        this.runFonts = runFonts;
        this.paragraphs = paragraphs(body);
    }

    /**
     * A Word form read and rewritten.
     *
     * @param docx        the document with every place rewritten into a named content control
     * @param spots       the places in the order the document holds them
     * @param placeFonts  the font each place is set in, by {@link FormSpot#tag()}, its family as the form names it
     * @param fonts       the font families the document names as written, each once, before any was replaced
     * @param rightMargin the right margin of the page in points, or 0 where the document names none
     */
    record ScannedForm(
            byte[] docx,
            List<FormSpot> spots,
            Map<String, TextFont> placeFonts,
            List<String> fonts,
            double rightMargin) {}

    /**
     * @param data the bytes of a Word document
     * @return the places it has, and the document rewritten so that each is exported as a form field
     * @throws IOException where the document cannot be read
     */
    static ScannedForm scan(byte[] data) throws IOException {
        return scan(data, Map.of());
    }

    /**
     * @param data    the bytes of a Word document
     * @param choices how each family the document names that is replaced is drawn instead
     * @return the places it has, and the document rewritten so that each is exported as a form field and
     *         drawn in the families and sizes chosen for those replaced
     * @throws IOException where the document cannot be read
     */
    static ScannedForm scan(byte[] data, Map<String, FontChoice> choices) throws IOException {
        var docx = DocxPackage.read(data);
        var fonts = FormFonts.named(docx);
        var runFonts = RunFonts.read(docx);
        FormFontSizes.scale(docx, runFonts, choices);
        FormBoldFaces.split(docx, runFonts, choices);
        var body = docx.xml(DocxPackage.BODY).orElseThrow();
        var scanner = new WordFormScanner(body, runFonts);
        scanner.read();
        scanner.rewrites.forEach(Runnable::run);
        docx.replace(DocxPackage.BODY, body);
        FormFonts.replace(docx, familiesOf(choices));
        return new ScannedForm(
                docx.bytes(), List.copyOf(scanner.spots), Map.copyOf(scanner.placeFonts), fonts, rightMargin(body));
    }

    private static Map<String, String> familiesOf(Map<String, FontChoice> choices) {
        var families = new HashMap<String, String>();
        choices.forEach((family, choice) -> families.put(family, choice.family()));
        return families;
    }

    /**
     * @param data the bytes of a Word document
     * @return the font families it names, each once
     * @throws IOException where the document cannot be read
     */
    static List<String> fontsOf(byte[] data) throws IOException {
        return FormFonts.named(DocxPackage.read(data));
    }

    /** Every paragraph of the body in document order, leaving out the copies kept for older programs. */
    private static List<Element> paragraphs(Document body) {
        var out = new ArrayList<Element>();
        var all = body.getElementsByTagNameNS(W, "p");
        for (int index = 0; index < all.getLength(); index++) {
            var paragraph = (Element) all.item(index);
            if (ancestor(paragraph, MC, "Fallback") == null) out.add(paragraph);
        }
        return out;
    }

    /**
     * Reads every line of the form. A paragraph broken by line breaks is as many lines as the page
     * shows, so the words on the line below a row of places are found in the same paragraph too.
     */
    private void read() {
        var lines = new ArrayList<Line>();
        for (var paragraph : paragraphs) {
            for (var shown : shownLines(pieces(paragraph))) lines.add(new Line(paragraph, shown));
        }
        for (int index = 0; index < lines.size(); index++) {
            var below = index + 1 < lines.size() ? lines.get(index + 1).pieces() : List.<Piece>of();
            label(lines.get(index).paragraph(), lines.get(index).pieces(), below);
        }
    }

    /**
     * One line as the page shows it.
     *
     * @param paragraph the paragraph it is part of
     * @param pieces    its words and places
     */
    private record Line(Element paragraph, List<Piece> pieces) {}

    /** The pieces of a paragraph split where a line break parts them. */
    private static List<List<Piece>> shownLines(List<Piece> pieces) {
        var lines = new ArrayList<List<Piece>>();
        var current = new ArrayList<Piece>();
        for (var piece : pieces) {
            if (!(piece instanceof Words words) || words.text().indexOf(LINE_BREAK) < 0) {
                current.add(piece);
                continue;
            }
            String[] parts = words.text().split(String.valueOf(LINE_BREAK), -1);
            for (int index = 0; index < parts.length; index++) {
                if (index > 0) {
                    lines.add(current);
                    current = new ArrayList<>();
                }
                if (!parts[index].isEmpty()) current.add(new Words(parts[index]));
            }
        }
        lines.add(current);
        return lines;
    }

    /** One part of a line: words, or a place to fill in. */
    private sealed interface Piece permits Words, Place {}

    private record Words(String text) implements Piece {}

    /**
     * A place before its label is known.
     *
     * @param number the place counted from one
     * @param shape  what is filled in and what counts as filled in
     * @param preset what it holds already, or null
     * @param hint   the words its form field shows as help, or empty
     */
    private record Place(
            int number, FieldShape shape, @Nullable String preset, String hint) implements Piece {

        FormSpotKind kind() {
            return shape.kind();
        }
    }

    /** The parts of one paragraph, its places registered and their rewrites queued. */
    private List<Piece> pieces(Element paragraph) {
        var walk = new LineWalk();
        walk.children(paragraph);
        return walk.pieces;
    }

    /**
     * Reads a paragraph in document order, following the state of the fields it passes through.
     */
    private final class LineWalk {
        private final List<Piece> pieces = new ArrayList<>();
        private final StringBuilder words = new StringBuilder();
        private final List<OpenField> fields = new ArrayList<>();

        /**
         * A field whose end has not been reached yet.
         *
         * @param begin  the run that begins it
         * @param data   its form field data, or null for a field that is no place to fill in
         * @param result whether its result is being read, which is what it shows
         */
        private record OpenField(Element begin, @Nullable Element data, boolean result) {}

        void children(Element parent) {
            for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
                if (child instanceof Element element) element(element);
            }
            flush();
        }

        private void element(Element element) {
            if (!W.equals(element.getNamespaceURI())) {
                if (!MC.equals(element.getNamespaceURI())) return;
                if ("AlternateContent".equals(element.getLocalName())) {
                    var choice = first(element, MC, "Choice");
                    if (choice != null) descend(choice);
                }
                return;
            }
            switch (element.getLocalName()) {
                case "r" -> run(element);
                case "sdt" -> control(element);
                case "hyperlink", "smartTag", "ins", "fldSimple", "customXml", "sdtContent" -> descend(element);
                default -> {}
            }
        }

        private void descend(Element parent) {
            for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
                if (child instanceof Element element) element(element);
            }
        }

        private void run(Element run) {
            for (Node child = run.getFirstChild(); child != null; child = child.getNextSibling()) {
                if (!(child instanceof Element part) || !W.equals(part.getNamespaceURI())) continue;
                switch (part.getLocalName()) {
                    case "fldChar" -> fieldChar(run, part);
                    case "t" -> {
                        if (shows()) words.append(part.getTextContent());
                    }
                    case "tab" -> {
                        if (shows()) words.append('\t');
                    }
                    case "br", "cr" -> {
                        if (shows()) words.append(LINE_BREAK);
                    }
                    case "sym" -> {
                        if (shows()) words.append(' ');
                    }
                    default -> {}
                }
            }
        }

        /** Whether text read now shows on the page: outside every field, or in the result of a plain one. */
        private boolean shows() {
            if (fields.isEmpty()) return true;
            var innermost = fields.getLast();
            return innermost.result() && innermost.data() == null;
        }

        private void fieldChar(Element run, Element fieldChar) {
            switch (fieldChar.getAttributeNS(W, "fldCharType")) {
                case "begin" -> fields.add(new OpenField(run, first(fieldChar, W, "ffData"), false));
                case "separate" -> {
                    if (fields.isEmpty()) return;
                    var open = fields.removeLast();
                    fields.add(new OpenField(open.begin(), open.data(), true));
                }
                case "end" -> {
                    if (fields.isEmpty()) return;
                    var open = fields.removeLast();
                    var data = open.data();
                    if (data != null && fields.isEmpty()) legacy(open.begin(), run, data);
                }
                default -> {}
            }
        }

        /** A form field of the old kind, from the run that begins it to the run that ends it. */
        private void legacy(Element begin, Element end, Element data) {
            if (begin.getParentNode() != end.getParentNode()) return;
            String preset = legacyPreset(data);
            String hint = attribute(first(data, W, "statusText"), "val");
            var place = place(FieldShape.ofLegacy(data), preset, hint, begin);
            var properties = first(begin, W, "rPr");
            rewrites.add(() -> {
                var control = namedControl(place, properties);
                var parent = begin.getParentNode();
                parent.insertBefore(control, begin);
                Node node = begin;
                while (node != null) {
                    var next = node.getNextSibling();
                    parent.removeChild(node);
                    if (node == end) break;
                    node = next;
                }
            });
        }

        private static @Nullable String legacyPreset(Element data) {
            var text = first(data, W, "textInput");
            if (text != null) return blankToNull(attribute(first(text, W, "default"), "val"));
            var box = first(data, W, "checkBox");
            if (box != null) {
                var checked = first(box, W, "checked");
                var fallback = first(box, W, "default");
                var state = checked != null ? checked : fallback;
                return state == null ? null : String.valueOf(!"0".equals(state.getAttributeNS(W, "val")));
            }
            var list = first(data, W, "ddList");
            if (list != null) return blankToNull(attribute(first(list, W, "listEntry"), "val"));
            return null;
        }

        /** A content control: a place to fill in where it is one, otherwise read as the text it holds. */
        private void control(Element control) {
            if (!fields.isEmpty()) return;
            var properties = first(control, W, "sdtPr");
            var content = first(control, W, "sdtContent");
            if (properties == null || content == null || holdsNoPlace(properties)) {
                if (content != null) descend(content);
                return;
            }
            var box = first(properties, W14, "checkbox");
            String preset = box != null
                    ? String.valueOf("1".equals(attribute(first(box, W14, "checked"), W14, "val")))
                    : first(properties, W, "showingPlcHdr") != null ? null : blankToNull(content.getTextContent());
            var firstRun = first(content, W, "r");
            var place = place(
                    FieldShape.ofControl(properties),
                    preset,
                    attribute(first(properties, W, "alias"), "val"),
                    firstRun == null ? control : firstRun);
            var runProperties = first(firstRun, W, "rPr");
            rewrites.add(() -> control.getParentNode().replaceChild(namedControl(place, runProperties), control));
        }

        private boolean holdsNoPlace(Element properties) {
            for (Node child = properties.getFirstChild(); child != null; child = child.getNextSibling()) {
                if (child instanceof Element element && NOT_FILLED_IN.contains(element.getLocalName())) return true;
            }
            return false;
        }

        /** A place, read for the font of the run that shows it, or of its paragraph where it holds no run. */
        private Place place(FieldShape shape, @Nullable String preset, String hint, Element shown) {
            flush();
            var place = new Place(++counted, shape, preset, hint);
            placeFonts.put(FormSpot.tagOf(place.number()), runFonts.of(shown));
            pieces.add(place);
            return place;
        }

        private void flush() {
            if (words.isEmpty()) return;
            pieces.add(new Words(words.toString()));
            words.setLength(0);
        }
    }

    /** Settles the labels of the places of one line and adds them to the places of the form. */
    private void label(Element paragraph, List<Piece> line, List<Piece> below) {
        var places = line.stream()
                .filter(Place.class::isInstance)
                .map(Place.class::cast)
                .toList();
        if (places.isEmpty()) return;
        var underneath = onlyPlaces(line) ? columns(below) : List.<String>of();
        boolean byColumn = underneath.size() == places.size();
        String heading = columnHeading(paragraph, line);
        for (int index = 0; index < line.size(); index++) {
            if (!(line.get(index) instanceof Place place)) continue;
            String label = place.kind() == FormSpotKind.CHECK ? after(line, index) : before(line, index);
            if (label.isEmpty() && place.kind() == FormSpotKind.CHECK) label = before(line, index);
            if (label.isEmpty() && byColumn) label = cleaned(underneath.get(places.indexOf(place)));
            if (label.isEmpty()) label = heading;
            if (label.isEmpty()) label = cleaned(place.hint());
            spots.add(new FormSpot(
                    place.number(),
                    place.kind(),
                    label,
                    place.preset(),
                    place.shape().limits()));
        }
    }

    /** The words between the place before and this one. */
    private static String before(List<Piece> line, int index) {
        var text = new StringBuilder();
        for (int at = index - 1; at >= 0 && line.get(at) instanceof Words words; at--) {
            text.insert(0, words.text());
        }
        return cleaned(text.toString());
    }

    /** The rest of the line after a check box, with any place in it written as an ellipsis. */
    private static String after(List<Piece> line, int index) {
        var text = new StringBuilder();
        for (int at = index + 1; at < line.size(); at++) {
            if (line.get(at) instanceof Words words) text.append(words.text());
            else if (line.get(at) instanceof Place place) {
                if (place.kind() == FormSpotKind.CHECK) break;
                text.append(" … ");
            }
        }
        return cleaned(text.toString());
    }

    private static boolean onlyPlaces(List<Piece> line) {
        return line.stream()
                .allMatch(piece ->
                        piece instanceof Place || ((Words) piece).text().isBlank());
    }

    /** The words of a line split where tabs or wide gaps part them. */
    private static List<String> columns(List<Piece> line) {
        if (line.isEmpty() || line.stream().anyMatch(Place.class::isInstance)) return List.of();
        var text = new StringBuilder();
        line.forEach(piece -> text.append(((Words) piece).text()));
        return COLUMNS.splitAsStream(text.toString().strip())
                .map(String::strip)
                .filter(part -> !part.isEmpty())
                .toList();
    }

    /**
     * The heading of the column a place stands in, where it stands alone in a cell below the first row of
     * a table.
     */
    private String columnHeading(Element paragraph, List<Piece> line) {
        boolean alone = line.stream()
                .noneMatch(
                        piece -> piece instanceof Words words && !words.text().isBlank());
        if (!alone) return "";
        var cell = ancestor(paragraph, W, "tc");
        if (cell == null || !(cell.getParentNode() instanceof Element row)) return "";
        if (!(row.getParentNode() instanceof Element table) || !"tbl".equals(table.getLocalName())) return "";
        var header = first(table, W, "tr");
        if (header == null || header == row) return "";
        int column = indexAmong(row, cell);
        var cells = children(header, W, "tc");
        if (column < 0 || column >= cells.size()) return "";
        return cleaned(cells.get(column).getTextContent());
    }

    private static int indexAmong(Element row, Element cell) {
        return children(row, W, "tc").indexOf(cell);
    }

    /** A content control for a place, holding nothing but what an empty place shows. */
    private Element namedControl(Place place, @Nullable Element runProperties) {
        String tag = FormSpot.tagOf(place.number());
        var control = body.createElementNS(W, "w:sdt");
        var properties = body.createElementNS(W, "w:sdtPr");
        control.appendChild(properties);
        if (runProperties != null) properties.appendChild(runProperties.cloneNode(true));
        properties.appendChild(valued(W, "w:alias", tag));
        properties.appendChild(valued(W, "w:tag", tag));
        var run = body.createElementNS(W, "w:r");
        if (runProperties != null) run.appendChild(runProperties.cloneNode(true));
        var text = body.createElementNS(W, "w:t");
        text.setAttributeNS("http://www.w3.org/XML/1998/namespace", "xml:space", "preserve");
        if (place.kind() == FormSpotKind.CHECK) {
            var box = body.createElementNS(W14, "w14:checkbox");
            box.appendChild(valued(W14, "w14:checked", "0"));
            properties.appendChild(box);
            text.setTextContent(BOX);
        } else {
            properties.appendChild(body.createElementNS(W, "w:text"));
            text.setTextContent(BLANK);
        }
        run.appendChild(text);
        var content = body.createElementNS(W, "w:sdtContent");
        content.appendChild(run);
        control.appendChild(content);
        return control;
    }

    private Element valued(String namespace, String name, String value) {
        var element = body.createElementNS(namespace, name);
        element.setAttributeNS(namespace, name.substring(0, name.indexOf(':') + 1) + "val", value);
        return element;
    }

    /** The right margin of the last section, which the body ends with, in points. */
    private static double rightMargin(Document body) {
        var sections = body.getElementsByTagNameNS(W, "sectPr");
        if (sections.getLength() == 0) return 0;
        var margin = first((Element) sections.item(sections.getLength() - 1), W, "pgMar");
        if (margin == null) return 0;
        try {
            return Integer.parseInt(margin.getAttributeNS(W, "right")) / 20.0;
        } catch (NumberFormatException none) {
            return 0;
        }
    }

    private static String cleaned(String text) {
        String words = SPACES.matcher(text).replaceAll(" ").strip();
        while (!words.isEmpty() && ":.…-_".indexOf(words.charAt(words.length() - 1)) >= 0) {
            words = words.substring(0, words.length() - 1).strip();
        }
        while (!words.isEmpty() && "…-_".indexOf(words.charAt(0)) >= 0) {
            words = words.substring(1).strip();
        }
        return words.length() > MAX_LABEL ? words.substring(0, MAX_LABEL).strip() : words;
    }

    private static @Nullable String blankToNull(@Nullable String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }

    private static @Nullable Element ancestor(Element element, String namespace, String name) {
        for (Node node = element.getParentNode(); node instanceof Element parent; node = parent.getParentNode()) {
            if (namespace.equals(parent.getNamespaceURI()) && name.equals(parent.getLocalName())) return parent;
        }
        return null;
    }
}
