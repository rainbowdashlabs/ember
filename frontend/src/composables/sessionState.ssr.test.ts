/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment node */
import {describe, expect, it} from 'vitest'
import {defineComponent, h, ref} from 'vue'
import {createSessionInfo} from '@/test/mocks/factories'
import {renderRequest} from '@/test/ssr'
import {getActingStation} from '@/util/actingStationState'
import {sessionWriter} from '@/util/sessionState'
import {useActingStation} from './useActingStation'
import {useCluster} from './useCluster'
import {useSession} from './useSession'

/** A page that changes shared state while it sets up when told to, and draws what it then holds. */
function page(write: () => void, read: () => string) {
    return defineComponent({
        props: {writes: {type: Boolean, default: false}},
        setup(props) {
            if (props.writes) write()
            return () => h('p', read())
        },
    })
}

/**
 * The reader's session and the station their screen acts for, rendered as two requests of one
 * server process. What the first reader's page set must not be what the second reader's page draws,
 * or the next request would go out under somebody else's station.
 */
describe('the session on the server', () => {
    it('keeps the station a screen acts for to the request that opened it', async () => {
        const Acting = page(
            () => useActingStation(ref('station-a')),
            () => String(getActingStation()),
        )

        expect(await renderRequest(() => h(Acting, {writes: true}))).toBe('<p>station-a</p>')
        expect(await renderRequest(() => h(Acting))).toBe('<p>null</p>')
    })

    it('keeps who is signed in to the request that heard it', async () => {
        const SignedIn = page(
            () => sessionWriter().setInfo(createSessionInfo({instanceUserType: 'ADMINISTRATOR'})),
            () => String(useSession().isAdmin()),
        )

        expect(await renderRequest(() => h(SignedIn, {writes: true}))).toBe('<p>true</p>')
        expect(await renderRequest(() => h(SignedIn))).toBe('<p>false</p>')
    })

    it('keeps the cluster a reader acts for to the request that chose it', async () => {
        const Clustered = page(
            () => useCluster().setActiveCluster('cluster-a'),
            () => String(useCluster().currentClusterId.value),
        )

        expect(await renderRequest(() => h(Clustered, {writes: true}))).toBe('<p>cluster-a</p>')
        expect(await renderRequest(() => h(Clustered))).toBe('<p>null</p>')
    })
})
