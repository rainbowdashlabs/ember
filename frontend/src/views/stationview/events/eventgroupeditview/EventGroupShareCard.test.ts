/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {mount} from '@vue/test-utils'
import {describe, expect, it, vi} from 'vitest'
import {ref} from 'vue'
import {FederationStatus, type PartnerResponse} from '@/api/generated/schema'
import EventGroupShareCard from './EventGroupShareCard.vue'
import PartnerPlacesRow from '../eventeditview/federationcard/PartnerPlacesRow.vue'
import {useEventFederationShare} from '../eventeditview/useEventFederationShare'

vi.mock('@/api', () => ({federation: {listPartners: vi.fn(async () => [])}}))

function partner(id: number, name: string): PartnerResponse {
  return {partner: {id, status: FederationStatus.ACTIVE}, partnerStationName: name} as unknown as PartnerResponse
}

function shared() {
  const share = useEventFederationShare(ref(true))
  share.partners.value = [partner(3, 'Nordwache'), partner(4, 'Südwache')]
  share.state.federationShared = true
  share.state.federationScope = 'SPECIFIC'
  share.state.federationPartnerIds = [3]
  share.state.federationPlaces = {3: {decides: true, budget: 2}}
  return share
}

function card(share: ReturnType<typeof useEventFederationShare>) {
  return mount(EventGroupShareCard, {
    props: {federationShare: share, canFederate: true},
    global: {stubs: {'font-awesome-icon': true}},
  })
}

/** The places of a partner of an event group count for all of it, and are kept with the share like an appointment's. */
describe('EventGroupShareCard', () => {
  it('offers places for each partner the event group is shared with', () => {
    const rows = card(shared()).findAllComponents(PartnerPlacesRow)

    expect(rows).toHaveLength(1)
    expect(rows[0]!.props('partnerName')).toBe('Nordwache')
    expect(rows[0]!.props('decides')).toBe(true)
    expect(rows[0]!.props('budget')).toBe(2)
  })

  it('says the places count for the whole event group', () => {
    const hint = card(shared()).get('[data-testid="partner-places-hint"]').text()

    expect(hint).toContain('ganze Terminreihe')
    expect(hint).not.toContain('Plätzen je Termin')
  })

  it('keeps a changed number of places in the share it saves', async () => {
    const share = shared()
    await card(share).findComponent(PartnerPlacesRow).vm.$emit('update:budget', 5)

    expect(share.state.federationPlaces).toEqual({3: {decides: true, budget: 5}})
  })
})
