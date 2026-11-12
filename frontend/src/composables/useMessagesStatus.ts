/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {readonly} from 'vue'
import type {MessagesStatus} from '@/api/generated/schema'
import {getStatus, setMail as saveMail} from '@/api/messages'

/**
 * Whether the reader can use messages and how many wait unread, as the header's messages button
 * shows it. Everybody starts without messages until the server says otherwise, so a member whose
 * guardian has not turned them on never sees the button flash up.
 */
export function useMessagesStatus() {
    const status = useState<MessagesStatus>('useMessagesStatus', () => ({available: false, unread: 0, mail: true}))

    /** Reads the status again, keeping the last one where it cannot be read. */
    async function refresh() {
        await getStatus().then(found => { status.value = found }).catch(() => {})
    }

    /** Turns the mail about unread messages on or off. */
    async function setMail(mail: boolean) {
        status.value = await saveMail(mail)
    }

    return {
        status: readonly(status),
        refresh,
        setMail,
    }
}
