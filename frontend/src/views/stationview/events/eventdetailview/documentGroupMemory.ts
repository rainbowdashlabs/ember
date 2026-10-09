/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {getItem, setItem} from '@/api/storage'
import {DOCUMENT_GROUPS, type DocumentGroup} from './documentGroups'

/** The whole section of one document, as opposed to one of its groups. */
export const SECTION_FOLD = 'section'

/** A part of the manager's view of a document that folds: the whole section or one of its groups. */
export type Fold = DocumentGroup | typeof SECTION_FOLD

/** Which parts the reader opened or closed. A part not named keeps its default. */
export type FoldChoices = Partial<Record<Fold, boolean>>

/**
 * The one stored value holding the choices. They hold for every appointment and every document alike, so
 * a manager who keeps "Erledigt" open finds it open everywhere.
 */
const STORAGE_KEY = 'document_groups'

const FOLDS: readonly string[] = [SECTION_FOLD, ...DOCUMENT_GROUPS]

/**
 * The parts of the manager's view of a document the reader opened or closed, as this browser remembers
 * them. Anything unreadable counts as no choice, since a part falling back to its default is harmless.
 *
 * @returns the choices
 */
export function loadFoldChoices(): FoldChoices {
    try {
        const parsed: unknown = JSON.parse(getItem(STORAGE_KEY) ?? '{}')
        if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return {}
        return Object.fromEntries(Object.entries(parsed)
            .filter(([fold, open]) => FOLDS.includes(fold) && typeof open === 'boolean'))
    } catch {
        return {}
    }
}

/**
 * Remembers the parts the reader opened or closed, where the reader allowed storing such comforts.
 * Without that the choice holds until the page is left.
 *
 * @param choices the choices
 */
export function saveFoldChoices(choices: FoldChoices): void {
    try {
        setItem(STORAGE_KEY, JSON.stringify(choices))
    } catch {
        return
    }
}
