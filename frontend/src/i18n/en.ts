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
    events: {
        notOpenToYou: 'Registration is open only to part of the station, not to you.',
        notOpenToHousehold: 'Registration is open only to part of the station, not to you or the people you look after.',
    },
    dashboard: {
        registrationNotOpen: 'Only for part of the station',
    },
    files: {
        noPreview: 'There is no preview for this kind of file. Download it to look at it.',
    },
    forms: {
        removedQuestionsLoseAnswers: 'The removed questions have already been answered. Saving deletes those answers, {count} in total. This cannot be undone.',
        removedOptionsLoseSelections: 'The removed options have already been chosen. Saving deletes them from the answers, {count} times in total. This cannot be undone.',
        removedQuestionsAndOptionsLoseAnswers: 'The removed questions and options have already been answered. Saving deletes {answers} answers and takes the removed options out of the answers {selections} times. This cannot be undone.',
        saveAndDeleteAnswers: 'Save and delete answers',
        addQuestion: 'Add question',
        dragQuestion: 'Drag to move the question',
        leaveUnsaved: 'The questions have unsaved changes. If you leave now, they are lost.',
        leaveAnyway: 'Leave and discard',
        duplicate: 'Duplicate',
        copyOf: 'Copy of {title}',
        questionShuffleStatements: 'Shuffle statements',
        questionMenu: {
            open: 'Question settings',
            describe: 'Add description',
            moveUp: 'Move up',
            moveDown: 'Move down',
            moveToPage: 'Move to {page}',
            duplicate: 'Duplicate question',
            remove: 'Delete question',
        },
        chips: {
            multiSelect: 'Multiple answers',
            multiSelectLimited: 'Multiple answers, {limit}',
            limit: {
                AT_MOST: 'at most {count}',
                AT_LEAST: 'at least {count}',
                EXACTLY: 'exactly {count}',
            },
            dropdown: 'As a dropdown',
            allowOther: 'With own answer',
            longAnswer: 'Long answer',
            ratingScale: 'Scale up to {max}',
            ratingIcon: {
                STAR: 'Stars',
                NUMBER: 'Numbers',
                HEART: 'Hearts',
                THUMB_UP: 'Thumbs',
            },
            likertScale: 'Scale {min} to {max}',
            likertLabels: 'Labelled scale',
            shuffled: 'Shuffled',
        },
        pages: {
            number: 'Page {number}',
            numberedTitle: 'Page {number}: {title}',
            title: 'Page title (optional)',
            description: 'Page description (optional)',
            menu: 'Page settings',
            moveUp: 'Move page up',
            moveDown: 'Move page down',
            remove: 'Remove page, keep its questions',
            add: 'Add page',
            drag: 'Drag to move the page',
            after: 'After this page',
            next: 'On to the next page',
            submit: 'Send the form',
            unreached: 'No path leads here',
        },
        branch: {
            question: 'An answer decides what comes next',
            none: 'None, everybody goes on the same way',
            untitled: 'Question {number}',
            unnamedOption: 'Option without text',
            otherwise: 'As after this page',
        },
        draft: {
            resumed: 'You continue where you stopped on {when}.',
            startOver: 'Start over',
        },
        completion: {
            title: 'After sending',
            message: 'Own message, for example: Thanks, see you on Saturday! (optional)',
            link: 'Link, for example to the event (optional)',
            linkLabel: 'Link text (optional)',
            hint: 'Without an own message the usual thanks is shown. A link starts with https:// or with / for a page of this station.',
            notALink: 'This is not a link yet, so it is not saved. It has to start with https://, http:// or /.',
        },
        preview: {
            toggle: 'Preview',
            path: 'Path so far: {path}',
            sent: 'Send',
            startOver: 'Start over',
            nothingSent: 'This is where the form would be sent. Nothing is saved in the preview.',
        },
        fill: {
            pageNumber: 'Page {number}',
            back: 'Back',
            next: 'Next',
            required: 'This question needs an answer.',
            backToForms: 'Back to the forms',
            changeAnswer: 'Change answer',
        },
    },
}
