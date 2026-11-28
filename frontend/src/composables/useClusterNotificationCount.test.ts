/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it, vi} from 'vitest'
import {useClusterNotificationCount} from './useClusterNotificationCount'

const count = vi.hoisted(() => vi.fn())

vi.mock('@/api/notifications', () => ({clusterInbox: {count}}))

/**
 * The number on the association's bell: the answer to the latest read wins, however the answers
 * arrive.
 */
describe('useClusterNotificationCount', () => {
    it('drops an older answer that arrives after a newer one', async () => {
        let answerOld: (value: number) => void = () => {}
        count
            .mockReturnValueOnce(new Promise<number>(resolve => { answerOld = resolve }))
            .mockResolvedValueOnce(0)
        const {count: shown, refresh} = useClusterNotificationCount()

        const older = refresh()
        await refresh()
        answerOld(4)
        await older

        expect(shown.value).toBe(0)
    })
})
