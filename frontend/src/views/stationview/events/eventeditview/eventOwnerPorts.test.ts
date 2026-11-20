/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it, vi} from 'vitest'
import {eventAttachmentPort, eventGroupAttachmentPort, eventGroupFederationPort, templateFederationPort} from './eventOwnerPorts'

const api = vi.hoisted(() => ({
  events: {listEventAttachments: vi.fn()},
  eventGroups: {listAttachments: vi.fn(), detachFile: vi.fn(), getPartnerPlaces: vi.fn(), setPartnerPlaces: vi.fn()},
}))
vi.mock('@/api', () => api)

describe('eventOwnerPorts', () => {
  it('leaves the event group\'s files out of what a part\'s editor writes', async () => {
    api.events.listEventAttachments.mockResolvedValue([{id: 1, fromEventGroup: true}, {id: 2, fromEventGroup: false}])

    expect(await eventAttachmentPort(12).list()).toEqual([{id: 2, fromEventGroup: false}])
  })

  it('writes an event group\'s files through the event group', async () => {
    api.eventGroups.listAttachments.mockResolvedValue([{id: 3, fromEventGroup: true}])
    const port = eventGroupAttachmentPort(7)

    expect(await port.list()).toEqual([{id: 3, fromEventGroup: true}])
    await port.detach(3)
    expect(api.eventGroups.detachFile).toHaveBeenCalledWith(7, 3)
  })

  it('reads and writes the places of an event group\'s partners through the event group', async () => {
    const view = {partnerId: 4, slotBudget: 3, partnerConfirms: true}
    api.eventGroups.getPartnerPlaces.mockResolvedValue([view])
    const places = eventGroupFederationPort(7).places

    expect(await places?.list()).toEqual([view])
    await places?.set(4, 3, true)
    expect(api.eventGroups.getPartnerPlaces).toHaveBeenCalledWith(7)
    expect(api.eventGroups.setPartnerPlaces).toHaveBeenCalledWith(7, 4, 3, true)
  })

  it('hands a template no places', () => {
    expect(templateFederationPort(9).places).toBeUndefined()
  })
})
