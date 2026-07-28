/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, ref} from 'vue'
import {events} from '@/api'

interface FieldDefaultEntry {
  source: string
  value: string
}

/**
 * Prefilled values the event hands to its attendance template fields, keyed by
 * field id.
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

  async function load(eventId: number) {
    const loaded = await events.getFieldDefaults(eventId)
    const m = new Map<number, FieldDefaultEntry>()
    for (const fd of loaded) {
      m.set(fd.fieldId, {source: fd.source, value: fd.value ?? ''})
    }
    defaults.value = m
  }

  async function save(eventId: number, always: boolean) {
    const entries = [...defaults.value.entries()]
        .filter(([, v]) => v.source)
        .map(([fieldId, v]) => ({fieldId, source: v.source, value: v.value || undefined}))
    if (entries.length > 0 || always) {
      await events.setFieldDefaults(eventId, entries)
    }
  }

  const props = computed(() => ({fieldDefaults: defaults.value}))

  const handlers = {
    'update:fieldDefaultSource': setSource,
    'update:fieldDefaultValue': setValue,
  }

  return {props, handlers, load, save}
}
