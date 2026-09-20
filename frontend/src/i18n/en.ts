/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/**
 * The interface in English, as far as it goes.
 *
 * <p>Deliberately partial. German is the fallback, so a key missing here is read in German rather
 * than shown as its own name, which is what makes starting with a handful of screens worth doing at
 * all: nothing breaks, and each key translated is one more thing an English reader understands.
 *
 * <p>Documents have been written in both languages for a long time, chosen per station. This is the
 * interface catching up with them, and the two should end up reading the same way round: a station
 * that has said nothing gets German in both.
 *
 * <p>Add a key here only once its German reads the same way. A half-translated sentence is worse
 * than an honest German one.
 */
export default {
    common: {
        send: 'Send',
        continue: 'Continue',
        loading: 'Loading...',
        error: 'That did not work. Try again, and please report it if it keeps happening.',
        cancel: 'Cancel',
        save: 'Save',
        export: 'Export',
        close: 'Close',
        previous: 'Previous',
        next: 'Next',
    },
    exportFormat: {
        title: 'Export',
        format: 'Format',
        csv: 'Spreadsheet (CSV)',
        pdf: 'PDF',
        separator: 'Separator',
        semicolon: 'Semicolon (;)',
        comma: 'Comma (,)',
        separatorHint: 'Spreadsheet software set to English usually expects the comma.',
    },
    attendanceReport: {
        period: 'Period',
        year: 'Year',
        month: 'Month',
        quarter: 'Quarter',
        week: 'Calendar week',
    },
    files: {
        noPreview: 'There is no preview for this kind of file. Download it to look at it.',
    },
}
