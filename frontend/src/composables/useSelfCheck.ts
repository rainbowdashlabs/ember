/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, ref, type Ref} from 'vue'
import type {InventoryItem, RequiredInventoryItem} from '@/api/inventory'
import {
    SelfCheckAnswer,
    type SelfCheckAnswerBody,
    type SelfCheckAnswerName,
    type SelfCheckRaisedKindName,
    type SelfCheckRaisedStateName,
    type SelfCheckResponse,
    type SelfCheckRow,
} from '@/api/selfChecks'

/**
 * One thing the member is asked about: a piece the station says they hold, or a place their role
 * says should be filled and is not.
 *
 * <p>The list is the walk's own, read from the same required-items shape, so the two cannot drift.
 * A place is identified by its inventory and its position among that inventory's empty places,
 * counted from zero, because the list is recomputed on every read and a bare position points at
 * something else the moment a group changes underneath it.
 */
export type SelfCheckEntry =
    | {type: 'piece'; key: string; item: InventoryItem; req: RequiredInventoryItem; position: number; total: number}
    | {type: 'place'; key: string; req: RequiredInventoryItem; slot: number; position: number; total: number}

/** What the member said about one entry, before it has been sent anywhere. */
export interface SelfCheckDraft {
    answer: SelfCheckAnswerName | null
    note: string
    typedInternalId: string
    /** The size they gave for a piece nobody wrote down, empty where they did not say. */
    sizeId: string
}

const EMPTY_DRAFT: SelfCheckDraft = {answer: null, note: '', typedInternalId: '', sizeId: ''}

/**
 * Why a member wants a piece swapped.
 *
 * <p>Both raise the same exchange, and the difference is what it says and what it may ask for: a
 * piece that no longer fits wants another size, a broken one wants a replacement and may perfectly
 * well want the same size back, or none at all.
 */
export const ExchangeCause = {
    DOES_NOT_FIT: 'DOES_NOT_FIT',
    BROKEN: 'BROKEN',
} as const

export type ExchangeCauseName = (typeof ExchangeCause)[keyof typeof ExchangeCause]

/** One thing the member set going about a piece, and how far it has actually got. */
export interface RaisedReport {
    kind: SelfCheckRaisedKindName
    state: SelfCheckRaisedStateName
}

/**
 * How far a report of one kind about one piece has got, or {@code null} where the member has not
 * raised one.
 *
 * <p>A dropped one does not count as raised: the answer it hung on came to nothing, so the member is
 * free to say the same thing again, and the screen says why it fell away rather than barring them.
 */
export function standingOf(reports: RaisedReport[], kind: SelfCheckRaisedKindName): SelfCheckRaisedStateName | null {
    const mine = reports.filter(report => report.kind === kind)
    if (mine.some(report => report.state === 'RAISED')) return 'RAISED'
    if (mine.some(report => report.state === 'WAITING')) return 'WAITING'
    if (mine.some(report => report.state === 'DROPPED')) return 'DROPPED'
    return null
}

/**
 * Whether a report about this entry has to wait for the station before it goes out.
 *
 * <p>It waits exactly where the member is putting a size right on the very line they are reporting
 * about. Putting a record right does not edit the piece: it writes a new one and takes the old one
 * off their name, so a report raised now would name the piece that is leaving and carry the size
 * they have just disowned. Nowhere else does anything wait.
 */
export function waitsForCorrection(entry: SelfCheckEntry, draft: SelfCheckDraft): boolean {
    return entry.type === 'piece' && draft.answer === SelfCheckAnswer.WRONG_RECORD && draft.sizeId !== ''
}

/** The size the member says the piece is, which is the one they put right where they put one right. */
export function statedSizeOf(entry: SelfCheckEntry, draft: SelfCheckDraft): number | null {
    if (entry.type !== 'piece') return null
    return draft.sizeId ? Number(draft.sizeId) : (entry.item.sizeId ?? null)
}

/** The key a saved answer hangs on, which is the piece where there is one and the place where not. */
export function entryKey(inventoryId: number, itemId?: number | null, slot?: number | null): string {
    return itemId != null ? `piece-${itemId}` : `place-${inventoryId}-${slot}`
}

/** Whether the station has already written this piece off, which is what makes it turn up rather than be had. */
export function recordedLost(item: InventoryItem): boolean {
    return item.custody === 'LOST' || item.lostAt != null
}

/** Whether a partner owns this piece, which is what takes the loss and the exchange off the table. */
export function borrowed(item: InventoryItem): boolean {
    return item.ownerKind === 'PARTNER_STATION'
}

/**
 * What a member may say about one entry.
 *
 * <p>A borrowed piece is offered only whether they have it, because a loss on a partner's gear
 * belongs on the lending request it came in on and there is nothing of the station's to swap it for.
 * A piece already written off is asked the one question that is still open about it.
 */
export function answersFor(entry: SelfCheckEntry): SelfCheckAnswerName[] {
    if (entry.type === 'place') return [SelfCheckAnswer.NEVER_HAD, SelfCheckAnswer.HAVE_ONE]
    if (recordedLost(entry.item)) return [SelfCheckAnswer.TURNED_UP]
    if (borrowed(entry.item)) return [SelfCheckAnswer.HAVE_IT, SelfCheckAnswer.DO_NOT_HAVE_IT]
    return [SelfCheckAnswer.HAVE_IT, SelfCheckAnswer.WRONG_RECORD]
}

/**
 * The member's side of a self-check: the list they walk, what they have said so far, and turning
 * that into the answers the endpoint takes.
 *
 * <p>Nothing here settles anything. A loss and an exchange are raised through the screens that
 * already accept them and take effect at once, so neither is one of these answers.
 *
 * <p>One line breaks that: the one where the member is putting a size right. There the report is
 * written down and waits, because the correction replaces the piece it would otherwise name.
 */
export function useSelfCheck(task: Ref<SelfCheckResponse | null>) {
    const drafts = ref<Map<string, SelfCheckDraft>>(new Map())

    /** The pieces of one inventory the station says the member holds. */
    function piecesOf(inventoryId: number): InventoryItem[] {
        return (task.value?.assigned ?? []).filter(item => item.inventoryId === inventoryId)
    }

    /** How many places of one inventory the member's role asks for and nothing fills. */
    function emptyPlaces(req: RequiredInventoryItem): number {
        return Math.max(0, req.requiredQuantity - req.assignedQuantity)
    }

    const entries = computed<SelfCheckEntry[]>(() => {
        const list: SelfCheckEntry[] = []
        for (const req of task.value?.required ?? []) {
            const pieces = piecesOf(req.inventoryId)
            const total = pieces.length + emptyPlaces(req)
            pieces.forEach((item, index) => {
                list.push({
                    type: 'piece',
                    key: entryKey(req.inventoryId, item.id),
                    item,
                    req,
                    position: index + 1,
                    total,
                })
            })
            for (let slot = 0; slot < emptyPlaces(req); slot++) {
                list.push({
                    type: 'place',
                    key: entryKey(req.inventoryId, null, slot),
                    req,
                    slot,
                    position: pieces.length + slot + 1,
                    total,
                })
            }
        }
        return list
    })

    const entriesFor = (inventoryId: number) => entries.value.filter(entry => entry.req.inventoryId === inventoryId)

    function draftOf(key: string): SelfCheckDraft {
        return drafts.value.get(key) ?? EMPTY_DRAFT
    }

    function setDraft(key: string, patch: Partial<SelfCheckDraft>) {
        const next = new Map(drafts.value)
        next.set(key, {...draftOf(key), ...patch})
        drafts.value = next
    }

    /**
     * Reads what was already saved back into the drafts, so coming back shows what was left.
     *
     * <p>What is on the screen and not yet saved survives this. The list is read again in the middle
     * of the walk, after a loss or an exchange is set going, and taking the server's word for the
     * whole map there would throw away every answer given since the last save.
     */
    function adoptSaved(rows: SelfCheckRow[]) {
        const next = new Map<string, SelfCheckDraft>()
        for (const row of rows) {
            next.set(entryKey(row.inventoryId, row.itemId, row.slot), {
                answer: row.answer,
                note: row.note ?? '',
                typedInternalId: row.typedInternalId ?? '',
                sizeId: row.sizeId == null ? '' : String(row.sizeId),
            })
        }
        for (const [key, draft] of drafts.value) next.set(key, draft)
        drafts.value = next
    }

    /** The answer waiting on one entry, or {@code null} where the member has said nothing about it. */
    function bodyOf(entry: SelfCheckEntry): SelfCheckAnswerBody | null {
        const draft = draftOf(entry.key)
        if (!draft.answer) return null
        const note = draft.note.trim()
        if (entry.type === 'piece') {
            const putsTheRecordRight = draft.answer === SelfCheckAnswer.WRONG_RECORD
            return {
                itemId: entry.item.id,
                answer: draft.answer,
                note,
                sizeId: putsTheRecordRight && draft.sizeId ? Number(draft.sizeId) : null,
            }
        }
        const holdsOne = draft.answer === SelfCheckAnswer.HAVE_ONE
        return {
            inventoryId: entry.req.inventoryId,
            slot: entry.slot,
            answer: draft.answer,
            note,
            typedInternalId: holdsOne ? draft.typedInternalId.trim() || null : null,
            sizeId: holdsOne && draft.sizeId ? Number(draft.sizeId) : null,
        }
    }

    const pending = computed(() => entries.value.map(bodyOf).filter((body): body is SelfCheckAnswerBody => body != null))

    /**
     * Which entries still ask for something.
     *
     * <p>A piece the station has already written off is not among them: leaving it alone says it is
     * still missing, which is a perfectly good answer and the one most members will give.
     */
    const unanswered = computed(() =>
        entries.value.filter(entry => {
            if (entry.type === 'piece' && recordedLost(entry.item)) return false
            return draftOf(entry.key).answer == null
        }),
    )

    /** What the member set going about a piece, and whether each of them has actually gone out. */
    const raisedFor = computed(() => {
        const map = new Map<number, RaisedReport[]>()
        for (const raised of task.value?.raised ?? []) {
            if (raised.itemId == null) continue
            map.set(raised.itemId, [...(map.get(raised.itemId) ?? []), {kind: raised.kind, state: raised.state}])
        }
        return map
    })

    /** What came back from the reviewer, keyed the way the entries are. */
    const refusedFor = computed(() => {
        const map = new Map<string, string>()
        for (const row of task.value?.rows ?? []) {
            if (row.state !== 'REFUSED') continue
            map.set(entryKey(row.inventoryId, row.itemId, row.slot), row.reviewerReason)
        }
        return map
    })

    return {
        entries,
        entriesFor,
        emptyPlaces,
        piecesOf,
        draftOf,
        setDraft,
        adoptSaved,
        pending,
        unanswered,
        raisedFor,
        refusedFor,
    }
}
