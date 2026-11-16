/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it, vi} from 'vitest'
import {eventAttachmentPort, eventGroupAttachmentPort} from './eventOwnerPorts'

const api = vi.hoisted(() => ({
  events: {listEventAttachments: vi.fn()},
  eventGroups: {listAttachments: vi.fn(), detachFile: vi.fn()},
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
})
