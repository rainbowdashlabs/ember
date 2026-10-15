/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ref} from 'vue'
import type {ConversationSummaryResponse} from '@/api/generated/schema'
import {listConversations} from '@/api/messages'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useMessageStream} from '@/composables/useMessageStream'

/**
 * The reader's conversations, the most recently active first, kept current from the message
 * stream and read again once a minute while the stream does not get through.
 */
export function useConversationList() {
    const conversations = ref<ConversationSummaryResponse[]>([])

    const {loading, failure} = useAsyncLoader(async () => {
        conversations.value = await listConversations()
    })

    /** Reads the list again without a spinner, keeping the last one where it cannot be read. */
    async function refresh() {
        await listConversations().then(found => { conversations.value = found }).catch(() => {})
    }

    useMessageStream(() => { void refresh() }, 60_000, () => { void refresh() })

    return {conversations, loading, failure, refresh}
}
