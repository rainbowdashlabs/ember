/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service.wordform;

import org.w3c.dom.Element;

import java.io.IOException;
import java.util.Map;

import static dev.chojo.ember.feature.generator.service.wordform.WordXml.W;
import static dev.chojo.ember.feature.generator.service.wordform.WordXml.W14;
import static dev.chojo.ember.feature.generator.service.wordform.WordXml.attribute;
import static dev.chojo.ember.feature.generator.service.wordform.WordXml.first;

/**
 * Writes example values into the places of a scanned Word form, so LibreOffice draws them in the form's
 * own fonts, the way they would look filled in by hand in Word. A box whose value says yes is ticked.
 */
final class FormSampleText {
    /** What a ticked box shows. */
    private static final String TICKED = "☒";

    private FormSampleText() {}

    /**
     * @param docx    a form as {@link WordFormScanner} rewrote it, every place a content control named by its tag
     * @param samples the value of each place by {@link FormSpot#tag()}
     * @return the form with the values written in
     * @throws IOException where the form cannot be read or written
     */
    static byte[] write(byte[] docx, Map<String, String> samples) throws IOException {
        var form = DocxPackage.read(docx);
        var body = form.xml(DocxPackage.BODY).orElseThrow();
        var controls = body.getElementsByTagNameNS(W, "sdt");
        for (int index = 0; index < controls.getLength(); index++) {
            var control = (Element) controls.item(index);
            var properties = first(control, W, "sdtPr");
            String sample = samples.get(attribute(first(properties, W, "tag"), "val"));
            if (sample != null) fill(control, properties, sample);
        }
        form.replace(DocxPackage.BODY, body);
        return form.bytes();
    }

    private static void fill(Element control, Element properties, String sample) {
        var text = first(first(first(control, W, "sdtContent"), W, "r"), W, "t");
        if (text == null) return;
        var box = first(properties, W14, "checkbox");
        if (box == null) {
            text.setTextContent(sample);
            return;
        }
        if (!FormSamples.YES.equals(sample)) return;
        var checked = first(box, W14, "checked");
        if (checked != null) checked.setAttributeNS(W14, "w14:val", "1");
        text.setTextContent(TICKED);
    }
}
