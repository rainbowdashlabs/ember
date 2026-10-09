/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {readonly} from 'vue'
import {accountLinks} from '@/api'
import type {LinkPrompt} from '@/api/generated/schema'

/**
 * The requests stations sent to link the signed-in account to one of their members, and whether the
 * prompt that asks about them after sign-in is open.
 *
 * <p>Asked for right after signing in, the same moment the consent status is, so somebody who was
 * invited or moved along with a station hears of it on their next visit. Answering one takes it off
 * the list here at once; the prompt closes when nothing is left to answer.
 */
export function useLinkRequests() {
    const prompts = useState<LinkPrompt[]>('useLinkRequests.prompts', () => [])
    const promptOpen = useState('useLinkRequests.promptOpen', () => false)

    async function refresh() {
        prompts.value = await accountLinks.waitingRequests()
    }

    /** Opens the prompt where something waits. A failure stays quiet: the account page asks again. */
    async function checkAfterSignIn() {
        try {
            await refresh()
            promptOpen.value = prompts.value.length > 0
        } catch {
            promptOpen.value = false
        }
    }

    function answered(uid: string) {
        prompts.value = prompts.value.filter(prompt => prompt.uid !== uid)
        if (prompts.value.length === 0) promptOpen.value = false
    }

    async function accept(uid: string) {
        await accountLinks.acceptRequest(uid)
        answered(uid)
    }

    async function decline(uid: string) {
        await accountLinks.declineRequest(uid)
        answered(uid)
    }

    function setPromptOpen(open: boolean) {
        promptOpen.value = open
    }

    return {
        prompts: readonly(prompts),
        promptOpen: readonly(promptOpen),
        refresh,
        checkAfterSignIn,
        accept,
        decline,
        setPromptOpen,
    }
}
