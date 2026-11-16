/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ref} from 'vue'
import {events} from '@/api'
import {isRecurringEvent} from '@/api/events'
import type {EventAttachment} from '@/api/generated/schema'
import {todayIsoDate} from '@/util/format'
import {fetchEditDetail, type useEventForm} from './useEventForm'
import type {useEventFieldDefaults} from './useEventFieldDefaults'
import type {useEventFederationShare} from './useEventFederationShare'
import type {useDocumentRequirements} from '../eventshared/useDocumentRequirements'
import {eventFederationPort, eventFieldDefaultsPort} from './eventOwnerPorts'

/** The appointment a new one is copied from, as the notice above the editor names it. */
export interface CopiedFrom {
  id: number
  name: string
}

/** The parts of the editor a copy fills, and how the editor reads the questions and fields it needs. */
export interface CopyTargets {
  form: ReturnType<typeof useEventForm>
  fieldDefaults: ReturnType<typeof useEventFieldDefaults>
  federationShare: ReturnType<typeof useEventFederationShare>
  documentRequirements: ReturnType<typeof useDocumentRequirements>
  /** Takes the questions an appointment asks into the editor. */
  loadRegistrationFields: (eventId: number) => Promise<void>
  /** The fields of the attendance sheet the form names now. */
  sheetFieldIds: () => number[]
  /** Those of them that name people. */
  memberFieldIds: () => number[]
}

/**
 * The appointment the address asks to copy, nothing where it names none that could be one.
 *
 * @param copyFrom the `copyFrom` query value
 */
export function copySourceOf(copyFrom: unknown): number | null {
  if (typeof copyFrom !== 'string') return null
  const id = Number(copyFrom)
  return Number.isInteger(id) && id > 0 ? id : null
}

/**
 * Fills the editor of a new appointment from the one it copies.
 *
 * <p>Everything the editor shows comes over: the form, the field starting values, the partner share,
 * the questions and the documents to bring. Starting values typed in for fields that name people are
 * left out, as the fields of the form are. The original's files are only named, since the server
 * links them once the copy exists.
 */
export function useEventCopy(targets: CopyTargets) {
  const copiedFrom = ref<CopiedFrom | null>(null)

  /**
   * Fills the editor from the original.
   *
   * @param sourceId the original
   * @return the original's own files, which the server hands to the copy when it is saved
   */
  async function copy(sourceId: number): Promise<EventAttachment[]> {
    const detail = await fetchEditDetail(sourceId)
    const nextDate = isRecurringEvent(detail.event.eventType)
        ? await events.getNextDate(sourceId).catch(() => null)
        : null
    targets.form.applyCopy(detail, todayIsoDate(), nextDate)
    copiedFrom.value = {id: sourceId, name: detail.event.name}
    const [files] = await Promise.all([
      events.listEventAttachments(sourceId),
      targets.fieldDefaults.load(eventFieldDefaultsPort(sourceId)),
      targets.federationShare.load(eventFederationPort(sourceId)),
      targets.loadRegistrationFields(sourceId),
      targets.documentRequirements.takeFrom({kind: 'events', id: sourceId}),
    ])
    targets.fieldDefaults.keepOnly(targets.sheetFieldIds())
    targets.fieldDefaults.forgetValuesOf(targets.memberFieldIds())
    return files.filter(file => !file.fromEventGroup)
  }

  return {copiedFrom, copy}
}
