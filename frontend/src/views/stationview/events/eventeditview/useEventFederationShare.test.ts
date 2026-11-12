/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {ref} from 'vue'
import {ShareScope} from '@/api/generated/schema'
import {useEventFederationShare} from './useEventFederationShare'
import {eventFederationPort, templateFederationPort} from './eventOwnerPorts'

const api = vi.hoisted(() => ({
    federation: {listPartners: vi.fn()},
    events: {
        getFederationShare: vi.fn(),
        setFederationShare: vi.fn(),
        removeFederationShare: vi.fn(),
        getPartnerPlaces: vi.fn(),
        setPartnerPlaces: vi.fn(),
        getTemplateFederationShare: vi.fn(),
        setTemplateFederationShare: vi.fn(),
        removeTemplateFederationShare: vi.fn(),
    },
}))
vi.mock('@/api', () => api)

/**
 * The share of an event and of an event template with partner stations. Only an event hands
 * partners places, so a template's share never asks for or writes any.
 */
describe('useEventFederationShare', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        api.federation.listPartners.mockResolvedValue([])
        api.events.getFederationShare.mockResolvedValue({shared: true, scope: ShareScope.SPECIFIC, partnerIds: [4]})
        api.events.getPartnerPlaces.mockResolvedValue([{partnerId: 4, partnerConfirms: true, slotBudget: 2}])
        api.events.getTemplateFederationShare.mockResolvedValue({shared: true, scope: ShareScope.SPECIFIC, partnerIds: [6]})
    })

    it('keeps the event share and its partner places as they were', async () => {
        const share = useEventFederationShare(ref(true))

        await share.load(eventFederationPort(12))
        await share.save(eventFederationPort(12))

        expect(api.events.getFederationShare).toHaveBeenCalledWith(12)
        expect(api.events.setFederationShare).toHaveBeenCalledWith(12, ShareScope.SPECIFIC, [4])
        expect(api.events.setPartnerPlaces).toHaveBeenCalledWith(12, 4, 2, true)
    })

    it('reads and writes a template share through the template routes, without places', async () => {
        const share = useEventFederationShare(ref(true))

        await share.load(templateFederationPort(31))
        expect(share.state.federationPartnerIds).toEqual([6])
        await share.save(templateFederationPort(31))

        expect(api.events.getTemplateFederationShare).toHaveBeenCalledWith(31)
        expect(api.events.setTemplateFederationShare).toHaveBeenCalledWith(31, ShareScope.SPECIFIC, [6])
        expect(api.events.getPartnerPlaces).not.toHaveBeenCalled()
        expect(api.events.setPartnerPlaces).not.toHaveBeenCalled()
        expect(api.events.setFederationShare).not.toHaveBeenCalled()
    })

    it('takes a template share away when it is switched off', async () => {
        const share = useEventFederationShare(ref(true))
        await share.load(templateFederationPort(31))

        share.state.federationShared = false
        await share.save(templateFederationPort(31))

        expect(api.events.removeTemplateFederationShare).toHaveBeenCalledWith(31)
    })

    it('leaves a template share alone for somebody who may not federate', async () => {
        const share = useEventFederationShare(ref(false))

        await share.load(templateFederationPort(31))
        await share.save(templateFederationPort(31))

        expect(api.events.getTemplateFederationShare).not.toHaveBeenCalled()
        expect(api.events.setTemplateFederationShare).not.toHaveBeenCalled()
        expect(api.events.removeTemplateFederationShare).not.toHaveBeenCalled()
    })
})
