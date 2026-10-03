/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service.font;

import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.generator.entity.FontOutline;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What an uploaded font file is, and which ones are refused before anything is kept. */
class FontFilesTest {

    @Test
    void aTrueTypeFontIsReadWithTheFamilyItCarries() {
        var inspection = FontFiles.inspect(TestFonts.lisu());
        assertEquals(FontOutline.TRUETYPE, inspection.outline());
        assertEquals(TestFonts.LISU_FAMILY, inspection.internalFamily());
        assertTrue(inspection.subsettable());
    }

    @Test
    void anOpenTypeFontWithPostScriptOutlinesIsTaken() {
        var inspection = FontFiles.inspect(TestFonts.lycian());
        assertEquals(FontOutline.CFF, inspection.outline());
        assertEquals("Noto Sans Lycian", inspection.internalFamily());
    }

    @Test
    void theBundledFontIsAFontToo() {
        assertEquals(BundledFont.FAMILY, FontFiles.inspect(BundledFont.data()).internalFamily());
    }

    /** Restricted licence embedding, alone or with other bits, and bitmap embedding only are refused. */
    @Test
    void aFontWhoseLicenceForbidsEmbeddingIsRefused() {
        for (int fsType : new int[] {0x0002, 0x0202, 0x0200, 0x0204}) {
            var refusal = assertThrows(
                    RefusalResponse.class, () -> FontFiles.inspect(TestFonts.withFsType(TestFonts.lisu(), fsType)));
            assertEquals(DocumentRefusal.DOCUMENT_FONT_EMBEDDING_FORBIDDEN, refusal.refusal(), "fsType " + fsType);
        }
    }

    /** Preview and print, editable, and installable embedding are all fine, the last without subsetting. */
    @Test
    void aFontThatAllowsEmbeddingIsTaken() {
        assertTrue(FontFiles.inspect(TestFonts.withFsType(TestFonts.lisu(), 0x0004))
                .subsettable());
        assertTrue(FontFiles.inspect(TestFonts.withFsType(TestFonts.lisu(), 0x0008))
                .subsettable());
        var whole = TestFonts.withFsType(TestFonts.lisu(), 0x0100);
        assertFalse(FontFiles.inspect(whole).subsettable());
        assertFalse(FontFiles.subsettable(whole));
    }

    @Test
    void aFileThatIsNoFontIsRefused() {
        var lisu = TestFonts.lisu();
        for (byte[] data : new byte[][] {
            "not a font at all".getBytes(StandardCharsets.US_ASCII),
            new byte[] {0, 1},
            "wOFF0000".getBytes(StandardCharsets.US_ASCII),
            Arrays.copyOf(lisu, 200)
        }) {
            var refusal = assertThrows(RefusalResponse.class, () -> FontFiles.inspect(data));
            assertEquals(DocumentRefusal.DOCUMENT_FONT_NOT_A_FONT, refusal.refusal());
        }
        assertFalse(FontFiles.subsettable(new byte[] {1, 2, 3, 4}));
    }
}
