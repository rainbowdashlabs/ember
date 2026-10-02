/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import {createI18n} from 'vue-i18n'
import IncomingPairRequests from './IncomingPairRequests.vue'
import OutgoingPairRequests from './OutgoingPairRequests.vue'
import deDE from '@/i18n/de-DE'
import {PairRequestStatus} from '@/api/generated/schema'

const i18n = () => createI18n({legacy: false, locale: 'de-DE', messages: {'de-DE': deDE, en: {}}})

/** The requests on the federation page: those waiting here, and those this station sent elsewhere. */
describe('pair request lists', () => {
  it('names the instance a request from elsewhere comes from and hands it back as such', async () => {
    const view = mount(IncomingPairRequests, {
      props: {
        local: [{id: 1, stationName: 'Wache Nachbar', createdAt: '2026-10-01T10:00:00Z'}],
        remote: [{id: 1, stationName: 'Wache Fern', instanceHost: 'fern.example', createdAt: '2026-10-02T10:00:00Z'}],
      },
      global: {plugins: [i18n()]},
    })

    expect(view.text()).toContain('Wache Nachbar')
    expect(view.text()).toContain('Von der Instanz fern.example')

    const accept = view.findAll('button').filter(button => button.text().includes(deDE.federation.acceptRequest))
    await accept[1]?.trigger('click')

    expect(view.emitted('accept')?.[0]?.[0]).toMatchObject({id: 1, instanceHost: 'fern.example'})
  })

  it('shows nothing while no request waits', () => {
    const view = mount(IncomingPairRequests, {props: {local: [], remote: []}, global: {plugins: [i18n()]}})

    expect(view.text()).toBe('')
  })

  it('says of each sent request whether it still waits or was declined', () => {
    const view = mount(OutgoingPairRequests, {
      props: {
        requests: [
          {id: 1, stationName: 'Wache Fern', instanceHost: 'fern.example', status: PairRequestStatus.PENDING,
            createdAt: '2026-10-02T10:00:00Z', answeredAt: null},
          {id: 2, stationName: 'Wache Weit', instanceHost: 'weit.example', status: PairRequestStatus.DECLINED,
            createdAt: '2026-10-01T10:00:00Z', answeredAt: '2026-10-02T10:00:00Z'},
        ],
      },
      global: {plugins: [i18n()]},
    })

    expect(view.text()).toContain(deDE.federation.waitingForAnswer)
    expect(view.text()).toContain(deDE.federation.declined)
    expect(view.text()).toContain('Auf der Instanz weit.example')
  })
})
