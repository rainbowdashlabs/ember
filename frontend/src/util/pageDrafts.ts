/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {getItem, setItem} from '@/api/storage'

/** Where every draft lives, as one value, so the disclosure has one key to name rather than many. */
const STORAGE_KEY = 'page_drafts'

/**
 * How long a draft is worth keeping.
 *
 * <p>Long enough to survive a closed laptop and a weekend, short enough that nobody is offered work
 * they have long since forgotten writing. A draft is a rescue, not a second copy of the page.
 */
export const DRAFT_LIFETIME_MS = 7 * 24 * 60 * 60 * 1000

/**
 * How many drafts are kept at once, newest first.
 *
 * <p>A browser gives the whole application a few megabytes, and a page of blocks is not small. An
 * editor left open on twenty pages must not be what fills it.
 */
const MAX_DRAFTS = 10

/** A draft of something being edited, and when it was last written. */
export interface PageDraft<T> {
    content: T
    savedAt: number
}

type DraftStore = Record<string, PageDraft<unknown>>

/**
 * Keeps what somebody is writing, so that leaving the page by accident does not throw it away.
 *
 * <p>Everything here is best effort. Storage may be refused outright, whether by the reader's
 * choice or by a browser in private mode, and the writing must carry on regardless: a rescue that
 * breaks the thing it is rescuing is worse than no rescue. So every path catches, and a draft that
 * cannot be written is simply not written.
 *
 * <p>Drafts are not the truth. What the server holds is, which is why one is offered rather than
 * applied: a week-old draft silently replacing the page somebody else has since corrected would
 * lose more than it saves.
 */
function read(): DraftStore {
    try {
        const raw = getItem(STORAGE_KEY)
        if (!raw) return {}
        const parsed = JSON.parse(raw) as DraftStore
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

/** Drops what has expired and, if there are still too many, the oldest of what is left. */
function pruned(store: DraftStore, now: number): DraftStore {
    const alive = Object.entries(store)
        .filter(([, draft]) => draft && now - draft.savedAt < DRAFT_LIFETIME_MS)
        .sort(([, a], [, b]) => b.savedAt - a.savedAt)
        .slice(0, MAX_DRAFTS)
    return Object.fromEntries(alive)
}

/**
 * Writes a draft for one thing being edited.
 *
 * @param key     what is being edited, such as a page or a knowledge base file
 * @param content whatever the editor holds, which is stored as it is
 */
export function saveDraft<T>(key: string, content: T): void {
    const now = Date.now()
    const store = pruned(read(), now)
    store[key] = {content, savedAt: now}
    write(pruned(store, now))
}

/** The draft for one thing, or null where there is none or it has expired. */
export function readDraft<T>(key: string): PageDraft<T> | null {
    const draft = pruned(read(), Date.now())[key]
    return draft ? (draft as PageDraft<T>) : null
}

/** Forgets one draft, which is what saving or discarding it amounts to. */
export function clearDraft(key: string): void {
    const store = pruned(read(), Date.now())
    if (!(key in store)) return
    delete store[key]
    write(store)
}
