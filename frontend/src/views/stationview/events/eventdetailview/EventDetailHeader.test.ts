/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {afterEach, describe, expect, it, vi} from 'vitest'
import {enableAutoUnmount, flushPromises} from '@vue/test-utils'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import {EventType, type StationEvent} from '@/api/generated/schema'
import EventDetailHeader from './EventDetailHeader.vue'

vi.mock('@/api', () => ({events: {deleteEvent: vi.fn()}}))

enableAutoUnmount(afterEach)

const EVENT = {id: 4, name: 'Übungsabend', eventType: EventType.RECURRING, cancelled: false, categoryId: null} as unknown as StationEvent

async function settle() {
  await flushPromises()
  await new Promise(resolve => setTimeout(resolve, 0))
  await flushPromises()
}

function inPage(testId: string): HTMLElement | null {
  return document.querySelector<HTMLElement>(`[data-testid="${testId}"]`)
}

async function mountHeader(canManageEvents: boolean, canWriteNews = false, extra: Record<string, unknown> = {}) {
  await mountSuspended(EventDetailHeader, {
    attachTo: document.body,
    props: {event: EVENT, canManageEvents, canWriteNews, effectiveDate: '2026-12-01', dateCancelled: false, datePast: false, ...extra},
  })
  await settle()
  inPage('event-actions-trigger')?.click()
  await settle()
}

/** What the head of an appointment's page offers, by what the reader may do. */
describe('EventDetailHeader', () => {
  it('gives whoever manages appointments Edit, calling off and deleting, with deleting last', async () => {
    await mountHeader(true)

    expect(inPage('event-edit')).not.toBeNull()
    const entries = [...document.querySelectorAll('[data-testid="event-actions"] [data-testid^="event-"]')]
        .map(element => element.getAttribute('data-testid'))
    expect(entries).toEqual(['event-cancel-date', 'event-cancel-series', 'event-delete'])
  })

  it('gives a member who only writes news the announcement and nothing else', async () => {
    await mountHeader(false, true)

    expect(inPage('event-edit')).toBeNull()
    expect(inPage('event-announce')).not.toBeNull()
    expect(inPage('event-delete')).toBeNull()
  })

  it('shows neither Edit nor a menu to a member who may do none of it', async () => {
    await mountHeader(false)

    expect(inPage('event-edit')).toBeNull()
    expect(inPage('event-actions-trigger')).toBeNull()
    expect(inPage('detail-page-back')).not.toBeNull()
  })

  it.each([
    ['a repeating appointment', {}, 'Alle Termine des wiederkehrenden Termins "Übungsabend" werden mit ihren Anmeldungen gelöscht'],
    ['a one-off', {event: {...EVENT, eventType: EventType.ONE_TIME}}, 'Termin "Übungsabend" wirklich löschen?'],
    ['a part of an event group', {eventGroupName: 'Grundlehrgang'}, 'verlässt die Terminreihe "Grundlehrgang"'],
  ])('asks before deleting %s what deleting it does', async (_kind, extra, question) => {
    await mountHeader(true, false, extra)

    inPage('event-delete')!.click()
    await settle()

    expect(document.querySelector('[role="dialog"]')?.textContent).toContain(question)
  })
})
