/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, ref} from 'vue'
import type {EventFieldDefault, FieldDefaultEntry} from '@/api/generated/schema'
import type {FieldDefaultsPort} from './eventOwnerPorts'

/** One pre-fill as the editors hold it: where the value comes from, and the value itself. */
export interface FieldDefaultValue {
  source: string
  value: string
}

/**
 * Prefilled values an event or an event template hands to its attendance template fields, keyed by
 * field id. Whose values they are comes in as a port with every read and write.
 */
export function useEventFieldDefaults() {
  const defaults = ref<Map<number, FieldDefaultValue>>(new Map())

  function entryFor(fieldId: number): FieldDefaultValue {
    return defaults.value.get(fieldId) ?? {source: '', value: ''}
  }

  function setSource(fieldId: number, source: string) {
    const existing = entryFor(fieldId)
    const m = new Map(defaults.value)
    if (!source) {
      m.delete(fieldId)
    } else {
      m.set(fieldId, {source, value: source === 'VALUE' ? existing.value : ''})
    }
    defaults.value = m
  }

  function setValue(fieldId: number, value: string) {
    const existing = entryFor(fieldId)
    const m = new Map(defaults.value)
    m.set(fieldId, {source: existing.source, value})
    defaults.value = m
  }

  /**
   * Drops the values of every field not named, which is what a change of attendance sheet does to
   * the fields of the sheet left behind.
   */
  function keepOnly(fieldIds: number[]) {
    const kept = new Set(fieldIds)
    const m = new Map([...defaults.value].filter(([fieldId]) => kept.has(fieldId)))
    if (m.size !== defaults.value.size) defaults.value = m
  }

  /**
   * Drops the values typed in by hand for these fields, keeping the ones taken from the appointment
   * itself. What a copy does to the fields that name people, since a copy puts nobody in charge.
   */
  function forgetValuesOf(fieldIds: number[]) {
    const forgotten = new Set(fieldIds)
    const m = new Map([...defaults.value].filter(([fieldId, entry]) => !(forgotten.has(fieldId) && entry.source === 'VALUE')))
    if (m.size !== defaults.value.size) defaults.value = m
  }

  /** Replaces the values with the ones an owner holds, as the server sends them. */
  function fromEntries(entries: EventFieldDefault[]) {
    defaults.value = new Map(entries.map(entry => [entry.fieldId, {source: entry.source, value: entry.value ?? ''}]))
  }

  /** The values as the server takes them: only the fields with a source, an empty value sent as none. */
  function toEntries(): FieldDefaultEntry[] {
    return [...defaults.value.entries()]
        .filter(([, entry]) => entry.source)
        .map(([fieldId, entry]) => ({fieldId, source: entry.source, value: entry.value || null}))
  }

  async function load(port: FieldDefaultsPort) {
    fromEntries(await port.get())
  }

  /**
   * Adds the starting values an event template gives, for the fields that hold none yet. A value
   * already chosen in the form stays, the same way applying a template keeps what the form holds.
   */
  async function takeFrom(port: FieldDefaultsPort) {
    const handed = await port.get()
    const m = new Map(defaults.value)
    for (const fd of handed) {
      if (!m.get(fd.fieldId)?.source) m.set(fd.fieldId, {source: fd.source, value: fd.value ?? ''})
    }
    defaults.value = m
  }

  async function save(port: FieldDefaultsPort, always: boolean) {
    const entries = toEntries()
    if (entries.length > 0 || always) {
      await port.set(entries)
    }
  }

  const props = computed(() => ({fieldDefaults: defaults.value}))

  const handlers = {
    'update:fieldDefaultSource': setSource,
    'update:fieldDefaultValue': setValue,
  }

  return {props, handlers, defaults, setSource, setValue, keepOnly, forgetValuesOf, fromEntries, toEntries, load, takeFrom, save}
}
