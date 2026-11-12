/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ref} from 'vue'
import type {ConversationMessageResponse, ConversationResponse} from '@/api/generated/schema'
import * as api from '@/api/messages'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useMessageStream} from '@/composables/useMessageStream'
import {withMessage} from '@/util/messageTimeline'

/**
 * One open conversation: its details, its messages from the newest back, and everything the reader
 * does in it.
 *
 * <p>Signals from the stream name the message that changed, which is then fetched alone; a change
 * to who is in the conversation fetches the details and whatever was written since. While the
 * stream does not get through, the newest page is asked for every 15 seconds and merged in.
 *
 * <p>Where the unread part starts is taken when the conversation opens and kept, so the line stays
 * where it was while the reader reads on.
 *
 * @param uid the conversation
 */
export function useConversation(uid: string) {
    const detail = ref<ConversationResponse | null>(null)
    const messages = ref<ConversationMessageResponse[]>([])
    const hasOlder = ref(false)
    const unreadAfter = ref<number | null>(null)
    const gone = ref(false)

    const {loading, failure} = useAsyncLoader(async () => {
        const [found, page] = await Promise.all([api.getConversation(uid), api.getMessages(uid)])
        detail.value = found
        messages.value = page.messages
        hasOlder.value = page.hasMore
        unreadAfter.value = found.lastReadMessageId
    })

    function upsert(message: ConversationMessageResponse) {
        messages.value = withMessage(messages.value, message)
    }

    function merge(found: readonly ConversationMessageResponse[]) {
        found.forEach(upsert)
    }

    async function loadOlder() {
        const first = messages.value[0]
        if (!first) return
        const page = await api.getMessages(uid, {before: first.id})
        messages.value = [...page.messages, ...messages.value]
        hasOlder.value = page.hasMore
    }

    async function refreshMessage(messageId: number) {
        await api.getMessage(uid, messageId).then(upsert).catch(() => {})
    }

    async function refreshDetail() {
        try {
            detail.value = await api.getConversation(uid)
        } catch {
            gone.value = true
        }
    }

    async function fetchNewer() {
        const last = messages.value.at(-1)
        const page = await api.getMessages(uid, last ? {after: last.id} : {}).catch(() => null)
        if (page) merge(page.messages)
    }

    async function poll() {
        await refreshDetail()
        if (gone.value) return
        const page = await api.getMessages(uid).catch(() => null)
        if (page) merge(page.messages)
    }

    useMessageStream(signal => {
        if (signal.conversationUid !== uid) return
        if (signal.kind === 'MESSAGE' && signal.messageId !== null) void refreshMessage(signal.messageId)
        if (signal.kind === 'CONVERSATION') void refreshDetail().then(fetchNewer)
    }, 15_000, () => { void poll() })

    /** Notes that the reader has read up to the newest message, where that is further than before. */
    async function markRead() {
        const newest = messages.value.at(-1)
        const current = detail.value
        if (!newest || !current || newest.id <= (current.lastReadMessageId ?? 0)) return
        current.lastReadMessageId = newest.id
        await api.markRead(uid, newest.id).catch(() => {})
    }

    async function send(text: string, replyToId: number | null) {
        upsert(await api.sendMessage(uid, text, replyToId))
    }

    async function edit(messageId: number, text: string) {
        upsert(await api.editMessage(uid, messageId, text))
    }

    async function remove(messageId: number) {
        await api.deleteMessage(uid, messageId)
        await refreshMessage(messageId)
    }

    /** Adds the reader's reaction, or takes it back where they reacted with that emoji already. */
    async function toggleReaction(message: ConversationMessageResponse, emoji: string) {
        const mine = message.reactions.some(group => group.emoji === emoji && group.mine)
        upsert(mine ? await api.takeBackReaction(uid, message.id, emoji) : await api.react(uid, message.id, emoji))
    }

    return {
        detail, messages, hasOlder, unreadAfter, gone, loading, failure,
        loadOlder, refreshDetail, markRead, send, edit, remove, toggleReaction,
    }
}
