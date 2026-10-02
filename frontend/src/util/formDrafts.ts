/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {getItem, setItem} from '@/api/storage'
import type {FormAnswerValue} from '@/api/generated/schema'
import {withoutKey} from '@/util/record'

/** Where every half-filled public form lives, as one value, so the disclosure names one key. */
const STORAGE_KEY = 'form_drafts'

/**
 * How long a half-filled form is kept: long enough to come back after a weekend, short enough that
 * nobody finds answers they have long forgotten giving.
 */
const LIFETIME_MS = 14 * 24 * 60 * 60 * 1000

/** How many half-filled forms are kept at once, newest first. */
const MAX_DRAFTS = 10

/** What a reader filled in of a public form so far, and when. */
export interface FormDraft {
    answers: Record<number, FormAnswerValue>
    /** The pages visited so far, the page to continue on last. */
    path: string[]
    savedAt: number
}

type DraftStore = Record<string, FormDraft>

/**
 * Keeps a half-filled public form in this browser only.
 *
 * <p>A visitor has no account to keep it under, and a draft on the server would need a visitor token
 * outliving the visit, for answers nobody has decided to send. So it stays on the device, where it is
 * also only kept once the visitor allowed storage for features. Everything here is best effort: a
 * browser refusing storage leaves the form working as before, just without a way back.
 */
function read(): DraftStore {
    try {
        const raw = getItem(STORAGE_KEY)
        const parsed = raw ? (JSON.parse(raw) as DraftStore) : {}
        return parsed && typeof parsed === 'object' ? parsed : {}
    } catch {
        return {}
    }
}

function write(store: DraftStore): void {
    try {
        setItem(STORAGE_KEY, JSON.stringify(store))
    } catch {
        void 0
    }
}

function pruned(store: DraftStore, now: number): DraftStore {
    return Object.fromEntries(Object.entries(store)
        .filter(([, draft]) => draft && now - draft.savedAt < LIFETIME_MS)
        .sort(([, a], [, b]) => b.savedAt - a.savedAt)
        .slice(0, MAX_DRAFTS))
}

/** The half-filled form kept under the given key, or null where there is none. */
export function readFormDraft(key: string): FormDraft | null {
    return pruned(read(), Date.now())[key] ?? null
}

/** Keeps what was filled in so far under the given key. */
export function saveFormDraft(key: string, draft: Omit<FormDraft, 'savedAt'>): void {
    const now = Date.now()
    const store = pruned(read(), now)
    store[key] = {...draft, savedAt: now}
    write(pruned(store, now))
}

/** Forgets the half-filled form kept under the given key. */
export function clearFormDraft(key: string): void {
    const store = pruned(read(), Date.now())
    if (!(key in store)) return
    write(withoutKey(store, key))
}
