/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {namesSomebody} from '@/components/input/restriction'
import type {RestrictionSelection} from '@/api/types'
import type {Translate} from '@/util/failure'

/** What stands between the parts of one summary. */
const SEPARATOR = ' · '

/**
 * The parts of a summary in one line, or "nicht festgelegt" where nothing is set. The summaries are
 * the lines the folded sections of the appointment, event group and template editors show, so a
 * closed section still tells whether it is worth opening.
 */
function joined(parts: string[], t: Translate): string {
    return parts.length > 0 ? parts.join(SEPARATOR) : t('formSections.nothingSet')
}

/**
 * The days before an appointment its reminders go out.
 *
 * @param days the days before, in any order
 */
export function remindersSummary(days: readonly number[], t: Translate): string {
    if (days.length === 0) return t('formSections.nothingSet')
    if (days.length === 1 && days[0] === 1) return t('formSections.remindersSetOne')
    return t('formSections.remindersSet', {days: [...days].sort((a, b) => a - b).join(', ')})
}

/**
 * How many registration questions are asked.
 *
 * @param count the questions
 */
export function questionsSummary(count: number, t: Translate): string {
    return count > 0 ? t('formSections.questionsSet', {count}) : t('formSections.nothingSet')
}

/**
 * The attendance list the appointment is taken on and how many fields it shows.
 *
 * @param sheetName      the attendance list's name, empty for none
 * @param fields         the fields
 * @param followsGroup   whether a part without a list of its own takes its event group's
 */
export function fieldsSummary(sheetName: string, fields: number, t: Translate, followsGroup = false): string {
    const parts: string[] = []
    if (sheetName) parts.push(t('formSections.sheetSet', {name: sheetName}))
    else if (followsGroup) parts.push(t('formSections.sheetFromGroup'))
    if (fields > 0) parts.push(t('formSections.fieldsSet', {count: fields}))
    return joined(parts, t)
}

/**
 * The minimum of confirmed sign-ups an appointment needs and how many days before.
 *
 * @param minimum the minimum, none for no rule
 * @param days    the days before the start it has to be reached, none for the day itself
 * @param closeDays the days before each date the sign-up of a repeating appointment closes
 */
export function turnoutSummary(minimum: number | null | undefined, days: number | null | undefined,
                               closeDays: number | null | undefined, t: Translate): string {
    const parts: string[] = []
    if (minimum) parts.push(minimumText(minimum, days, t))
    if (closeDays != null) {
        parts.push(closeDays === 1 ? t('formSections.closeDaysSetOne') : t('formSections.closeDaysSet', {days: closeDays}))
    }
    return joined(parts, t)
}

/** The minimum and by how many days before the start it has to be reached, a single day named as one. */
function minimumText(minimum: number, days: number | null | undefined, t: Translate): string {
    if (days == null) return t('formSections.minimumSet', {minimum})
    if (days === 1) return t('formSections.minimumBeforeOne', {minimum})
    return t('formSections.minimumBefore', {minimum, days})
}

/**
 * Who may sign up and who may see the appointment, each as all or restricted.
 *
 * @param registration who may sign up
 * @param view         who may see it
 */
export function audienceSummary(registration: RestrictionSelection, view: RestrictionSelection, t: Translate): string {
    const signUp = namesSomebody(registration) ? t('formSections.signUpRestricted') : t('formSections.signUpAll')
    const see = namesSomebody(view) ? t('formSections.viewRestricted') : t('formSections.viewAll')
    return `${signUp}${SEPARATOR}${see}`
}

/**
 * With which partner stations the appointment is shared.
 *
 * @param shared     whether it is shared at all
 * @param scope      the share scope, `SPECIFIC` for a choice of partners
 * @param partnerIds the partners chosen
 */
export function sharingSummary(shared: boolean, scope: string | null | undefined, partnerIds: readonly number[], t: Translate): string {
    if (!shared) return t('formSections.notShared')
    if (scope === 'SPECIFIC') {
        return partnerIds.length > 0 ? t('formSections.sharedWith', {count: partnerIds.length}) : t('formSections.notShared')
    }
    return t('formSections.sharedWithAll')
}

/**
 * How many documents to bring are asked for and how many files are handed over.
 *
 * @param documents the documents to bring
 * @param files     the files
 */
export function documentsSummary(documents: number, files: number, t: Translate): string {
    const parts: string[] = []
    if (documents > 0) parts.push(t('formSections.documentsSet', {count: documents}))
    if (files > 0) parts.push(t('formSections.filesSet', {count: files}))
    return joined(parts, t)
}
