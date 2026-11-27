/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, reactive, ref, type Ref} from 'vue'
import {federation} from '@/api'
import {FederationStatus, type PartnerResponse, type ShareScope} from '@/api/generated/schema'
import {modelBindings} from './modelBindings'
import type {FederationSharePort} from './eventOwnerPorts'

/** The scope the share picker's choice names, everybody unless it names a choice of partners. */
function shareScopeOf(pickerScope: string): ShareScope {
  return pickerScope === 'SPECIFIC' ? 'SPECIFIC' : 'ALL_PARTNERS'
}

/**
 * Whether and with which partner stations the edited event or event template is shared. Whose share
 * it is comes in as a port with every read and write; only an event's port arranges partner places.
 */
export function useEventFederationShare(canFederate: Ref<boolean>) {
  const partners = ref<PartnerResponse[]>([])

  const state = reactive({
    federationShared: false,
    federationScope: 'ALL_PARTNERS',
    federationPartnerIds: [] as number[],
    /**
     * What each partner may do with this appointment or event group, keyed by partner.
     *
     * <p>A partner missing from here is the arrangement nobody configured: this station decides, with
     * no cap, which is how every shared appointment worked before this existed.
     */
    federationPlaces: {} as Record<number, {decides: boolean; budget: number | null}>,
  })

  const {props: modelProps, handlers} = modelBindings(state)

  const props = computed(() => ({partners: partners.value, ...modelProps.value}))

  /** Reads the partners the picker offers, for a share that does not exist yet. None where they cannot be read. */
  async function loadPartners() {
    try {
      partners.value = await federation.listPartners()
    } catch {
      partners.value = []
    }
  }

  /**
   * Reads the partners and how the opened appointment or template is shared. A share that cannot be
   * read fails the load, since a form left at "not shared" would stop the sharing on the next save.
   */
  async function load(port: FederationSharePort) {
    await loadPartners()
    if (!canFederate.value) return
    const share = await port.get()
    state.federationShared = share.shared
    if (share.shared) {
      state.federationScope = share.scope ?? 'ALL_PARTNERS'
      state.federationPartnerIds = share.partnerIds ?? []
    }
    if (!port.places) return
    try {
      const places = await port.places.list()
      state.federationPlaces = Object.fromEntries(
          places.map(place => [place.partnerId, {decides: place.partnerConfirms, budget: place.slotBudget}]))
    } catch {
      state.federationPlaces = {}
    }
  }

  /**
   * Takes over how an event template shares its appointments, which the next save then writes.
   *
   * <p>Only somebody who may share appointments may set a share, so for anybody else a template that
   * shares is reported as skipped and the form is left as it is. A template that shares nothing
   * leaves the form as it is too. Partners the template names that are not active partners of this
   * station, the only ones the picker offers, are dropped and counted, and a choice of partners left
   * with nobody shares nothing.
   */
  async function takeFrom(port: FederationSharePort): Promise<{skipped: boolean; droppedPartners: number}> {
    const share = await port.get()
    if (!share.shared) return {skipped: false, droppedPartners: 0}
    if (!canFederate.value) return {skipped: true, droppedPartners: 0}
    if (partners.value.length === 0) partners.value = await federation.listPartners()
    const scope = shareScopeOf(share.scope ?? 'ALL_PARTNERS')
    if (scope !== 'SPECIFIC') {
      state.federationShared = true
      state.federationScope = scope
      return {skipped: false, droppedPartners: 0}
    }
    const known = new Set(partners.value
        .filter(entry => entry.partner.status === FederationStatus.ACTIVE)
        .map(entry => entry.partner.id))
    const named = share.partnerIds ?? []
    const kept = named.filter(id => known.has(id))
    state.federationShared = kept.length > 0
    state.federationScope = scope
    state.federationPartnerIds = kept
    return {skipped: false, droppedPartners: named.length - kept.length}
  }

  /**
   * Writes the share, and only then what each partner the share covers was given, because a partner
   * that is no longer shared with has nothing to arrange and the server would have nothing to hang it on.
   *
   * <p>Both of these used to swallow whatever came back, apparently on the belief that they refuse a
   * no-op. They do not: taking a share away answers the same whether or not one was there, and
   * setting a partner's places answers the same whether or not anything changed. So the only things
   * either could ever have swallowed were real: an appointment that has gone, one belonging to
   * another station, a missing permission, or a fault. Un-sharing an appointment and being shown it
   * worked whatever happened is the failure this whole change is about.
   */
  async function save(port: FederationSharePort) {
    if (!canFederate.value) return
    if (state.federationShared) {
      const scope = shareScopeOf(state.federationScope)
      const partnerIds = scope === 'SPECIFIC' ? state.federationPartnerIds : undefined
      await port.set(scope, partnerIds)
    } else {
      await port.remove()
    }
    if (!port.places) return
    for (const [partnerId, place] of Object.entries(state.federationPlaces)) {
      if (!coveredByShare(Number(partnerId))) continue
      await port.places.set(Number(partnerId), place.budget, place.decides)
    }
  }

  /**
   * Whether the share as saved covers a partner, and so whether there is anything to arrange with it:
   * nobody where nothing is shared, the partners named where the share names some, and everybody where
   * it goes to every partner. The server drops the arrangements a share no longer covers and refuses
   * new ones for such a partner.
   */
  function coveredByShare(partnerId: number): boolean {
    if (!state.federationShared) return false
    if (shareScopeOf(state.federationScope) !== 'SPECIFIC') return true
    return state.federationPartnerIds.includes(partnerId)
  }

  return {props, handlers, state, partners, load, loadPartners, takeFrom, save}
}
