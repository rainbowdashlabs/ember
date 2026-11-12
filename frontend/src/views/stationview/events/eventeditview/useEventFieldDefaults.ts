/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, ref} from 'vue'
import type {FieldDefaultsPort} from './eventOwnerPorts'

interface FieldDefaultEntry {
  source: string
  value: string
}

/**
 * Prefilled values an event or an event template hands to its attendance template fields, keyed by
 * field id. Whose values they are comes in as a port with every read and write.
 */
export function useEventFieldDefaults() {
  const defaults = ref<Map<number, FieldDefaultEntry>>(new Map())

  function entryFor(fieldId: number): FieldDefaultEntry {
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

  async function load(port: FieldDefaultsPort) {
    const loaded = await port.get()
    const m = new Map<number, FieldDefaultEntry>()
    for (const fd of loaded) {
      m.set(fd.fieldId, {source: fd.source, value: fd.value ?? ''})
    }
    defaults.value = m
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
    const entries = [...defaults.value.entries()]
        .filter(([, v]) => v.source)
        .map(([fieldId, v]) => ({fieldId, source: v.source, value: v.value || undefined}))
    if (entries.length > 0 || always) {
      await port.set(entries)
    }
  }

  const props = computed(() => ({fieldDefaults: defaults.value}))

  const handlers = {
    'update:fieldDefaultSource': setSource,
    'update:fieldDefaultValue': setValue,
  }

  return {props, handlers, defaults, setSource, setValue, keepOnly, load, takeFrom, save}
}
