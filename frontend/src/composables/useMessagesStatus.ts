/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {readonly} from 'vue'
import type {MessagesStatus, StationDigest} from '@/api/generated/schema'
import {getStatus, updateSettings} from '@/api/messages'

/**
 * Whether the reader can use messages and how many wait unread, as the header's messages button
 * shows it. Everybody starts without messages until the server says otherwise, so a member whose
 * guardian has not turned them on never sees the button flash up.
 */
export function useMessagesStatus() {
    const status = useState<MessagesStatus>('useMessagesStatus', () => ({available: false, unread: 0, mail: true, stationDigest: 'MENTIONS'}))

    /** Reads the status again, keeping the last one where it cannot be read. */
    async function refresh() {
        await getStatus().then(found => { status.value = found }).catch(() => {})
    }

    /** Turns the daily mail about unread messages on or off. */
    async function setMail(mail: boolean) {
        status.value = await updateSettings({mail})
    }

    /** Chooses what the daily mail takes from station chats. */
    async function setStationDigest(stationDigest: StationDigest) {
        status.value = await updateSettings({stationDigest})
    }

    return {
        status: readonly(status),
        refresh,
        setMail,
        setStationDigest,
    }
}
