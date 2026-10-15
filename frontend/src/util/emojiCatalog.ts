/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {CompactEmoji, MessagesDataset} from 'emojibase'
import {browserShallowRef} from '@/util/browserState'

/** One emoji as the picker offers it, with what it is searched by. */
export interface PickableEmoji {
    unicode: string
    label: string
    words: string
}

/** One group of the picker, in the order a phone keyboard shows them. */
export interface EmojiGroup {
    key: string
    label: string
    emoji: PickableEmoji[]
}

/** The group Unicode files the skin tones and hair styles under, which are parts and not emoji to pick. */
const COMPONENT_GROUP = 2

const catalog = browserShallowRef<Promise<EmojiGroup[]> | null>(null)

function pickable(entry: CompactEmoji): PickableEmoji {
    return {
        unicode: entry.unicode,
        label: entry.label,
        words: [entry.label, ...(entry.tags ?? []), ...(entry.shortcodes ?? [])].join(' ').toLocaleLowerCase(),
    }
}

async function read(): Promise<EmojiGroup[]> {
    const [{default: data}, {default: messages}] = await Promise.all([
        import('emojibase-data/de/compact.json'),
        import('emojibase-data/de/messages.json'),
    ])
    const dataset = messages as MessagesDataset
    return dataset.groups
        .filter(group => group.order !== COMPONENT_GROUP)
        .sort((a, b) => a.order - b.order)
        .map(group => ({
            key: group.key,
            label: group.message,
            emoji: (data as CompactEmoji[])
                .filter(entry => entry.group === group.order)
                .sort((a, b) => (a.order ?? 0) - (b.order ?? 0))
                .map(pickable),
        }))
}

/**
 * Every standard emoji in German, by group. The list is large, so it is fetched only when the
 * picker first opens and kept for the rest of the visit.
 *
 * @returns the groups
 */
export function emojiCatalog(): Promise<EmojiGroup[]> {
    if (!catalog.value) {
        catalog.value = read().catch(e => {
            catalog.value = null
            throw e
        })
    }
    return catalog.value
}

/**
 * The emoji whose name or keywords hold every word of the search.
 *
 * @param groups the catalog
 * @param search what the reader typed
 * @returns the matching emoji, in catalog order
 */
export function searchEmoji(groups: readonly EmojiGroup[], search: string): PickableEmoji[] {
    const words = search.toLocaleLowerCase().split(/\s+/).filter(Boolean)
    return groups.flatMap(group => group.emoji).filter(entry => words.every(word => entry.words.includes(word)))
}
