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
  }

  async function save(eventId: number) {
    if (!canFederate.value) return
    if (state.federationShared) {
      const partnerIds = state.federationScope === 'SPECIFIC_PARTNERS' ? state.federationPartnerIds : undefined
      await events.setFederationShare(eventId, state.federationScope, partnerIds)
    } else {
      await events.removeFederationShare(eventId).catch(() => {})
    }
  }

  return {props, handlers, load, save}
}
