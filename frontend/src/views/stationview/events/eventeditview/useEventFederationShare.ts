/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, reactive, ref, type Ref} from 'vue'
import {events, federation} from '@/api'
import type {PartnerResponse} from '@/api/federation'
import {modelBindings} from './modelBindings'

/**
 * Whether and with which partner stations the edited event is shared.
 */
export function useEventFederationShare(canFederate: Ref<boolean>) {
  const partners = ref<PartnerResponse[]>([])

  const state = reactive({
    federationShared: false,
    federationScope: 'ALL_PARTNERS',
    federationPartnerIds: [] as number[],
    /**
     * What each partner may do with this appointment, keyed by partner.
     *
     * <p>A partner missing from here is the arrangement nobody configured: this station decides, with
     * no cap, which is how every shared appointment worked before this existed.
     */
    federationPlaces: {} as Record<number, {decides: boolean; budget: number | null}>,
  })

  const {props: modelProps, handlers} = modelBindings(state)

  const props = computed(() => ({partners: partners.value, ...modelProps.value}))

  async function load(eventId: number) {
    try {
      partners.value = await federation.listPartners()
    } catch {
      partners.value = []
    }
    if (!canFederate.value) return
    try {
      const share = await events.getFederationShare(eventId)
      state.federationShared = share.shared
      if (share.shared) {
        state.federationScope = share.scope ?? 'ALL_PARTNERS'
        state.federationPartnerIds = share.partnerIds ?? []
      }
    } catch {
      state.federationShared = false
    }
    try {
      const places = await events.getPartnerPlaces(eventId)
      state.federationPlaces = Object.fromEntries(
          places.map(place => [place.partnerId, {decides: place.partnerConfirms, budget: place.slotBudget}]))
    } catch {
      state.federationPlaces = {}
    }
  }

  /**
   * Writes the share, and only then what each partner was given, because a partner that is no longer
   * shared with has nothing to arrange and the server would have nothing to hang it on.
   *
   * <p>Both of these used to swallow whatever came back, apparently on the belief that they refuse a
   * no-op. They do not: taking a share away answers the same whether or not one was there, and
   * setting a partner's places answers the same whether or not anything changed. So the only things
   * either could ever have swallowed were real: an appointment that has gone, one belonging to
   * another station, a missing permission, or a fault. Un-sharing an appointment and being told it
   * worked whatever happened is the failure this whole change is about.
   */
  async function save(eventId: number) {
    if (!canFederate.value) return
    if (state.federationShared) {
      const partnerIds = state.federationScope === 'SPECIFIC_PARTNERS' ? state.federationPartnerIds : undefined
      await events.setFederationShare(eventId, state.federationScope, partnerIds)
    } else {
      await events.removeFederationShare(eventId)
    }
    for (const [partnerId, place] of Object.entries(state.federationPlaces)) {
      await events.setPartnerPlaces(eventId, Number(partnerId), place.budget, place.decides)
    }
  }

  return {props, handlers, load, save}
}
