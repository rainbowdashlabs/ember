/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import DesktopHeaderRow from './fieldtable/DesktopHeaderRow.vue'
import DesktopFieldRow from './fieldtable/DesktopFieldRow.vue'
import MobileFieldCard from './fieldtable/MobileFieldCard.vue'
import type {EditableField, FieldSwitchName} from '@/api/profileFields'
import {useBreakpoint} from '@/composables/useBreakpoint'
import {useElementWidth} from '@/composables/useElementWidth'
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {fieldTypeLabel} from './fieldTypes'
import {fitsFieldGrid} from './fieldtable/fieldGrid'
import {useFieldsCapabilities, type WritabilityName} from '@/composables/useFieldsConfig'

/**
 * The questions a station asks, each written once and listed by name.
 *
 * <p>Nothing is dragged here. The order a question stands in belongs to the form it stands on, and one
 * question stands on several, so ordering is done on the form rather than on the list of questions.
 *
 * @param audienceCount how many audiences each question is put to, by question id
 */
const props = defineProps<{
  fields: EditableField[]
  selectedId: number | null
  audienceCount: Record<number, number>
  /** The questions ticked for putting to somebody all at once. */
  checkedIds: Set<number>
}>()

const {isMobile} = useBreakpoint()
const capabilities = useFieldsCapabilities()

const table = ref<HTMLElement | null>(null)
const width = useElementWidth(table)

/**
 * Tiles once the table itself is too narrow for its columns, which in the half of a wide page happens
 * long after the window has stopped counting as small. Before the first measurement, the window decides.
 */
const tiled = computed(() => width.value === null
    ? isMobile.value
    : !fitsFieldGrid(width.value, capabilities.writability))

const emit = defineEmits<{
  select: [field: EditableField]
  toggleChecked: [field: EditableField]
  edit: [field: EditableField]
  delete: [field: EditableField]
  toggleConfig: [field: EditableField, key: FieldSwitchName, value: boolean]
  toggleKeepOnArchive: [field: EditableField, value: boolean]
  toggleRequired: [field: EditableField, value: boolean]
  toggleReadonly: [field: EditableField, value: boolean]
  setWritability: [field: EditableField, level: WritabilityName]
}>()

const {t} = useI18n()

function typeLabel(value: string): string {
  return fieldTypeLabel(t, value)
}

function audiencesOf(field: EditableField): number {
  return props.audienceCount[field.id] ?? 0
}
</script>

<template>
  <div ref="table">
    <template v-if="tiled">
      <MobileFieldCard
          v-for="field in fields"
          :key="field.id"
          :audiences="audiencesOf(field)"
          :field="field"
          :selected="field.id === selectedId"
          :checked="props.checkedIds.has(field.id)"
          :type-label="typeLabel(field.fieldType ?? '')"
          @select="emit('select', field)"
          @toggle-checked="emit('toggleChecked', field)"
          @delete="emit('delete', field)"
          @edit="emit('edit', field)"
          @toggle-config="(f, k, v) => emit('toggleConfig', f, k, v)"
          @toggle-keep-on-archive="(f, v) => emit('toggleKeepOnArchive', f, v)"
          @toggle-required="(f, v) => emit('toggleRequired', f, v)"
          @toggle-readonly="(f, v) => emit('toggleReadonly', f, v)"
          @set-writability="(f, level) => emit('setWritability', f, level)"/>
    </template>

    <template v-else>
      <DesktopHeaderRow/>
      <DesktopFieldRow
          v-for="field in fields"
          :key="field.id"
          :audiences="audiencesOf(field)"
          :field="field"
          :selected="field.id === selectedId"
          :checked="props.checkedIds.has(field.id)"
          :type-label="typeLabel(field.fieldType ?? '')"
          @select="emit('select', field)"
          @toggle-checked="emit('toggleChecked', field)"
          @delete="emit('delete', field)"
          @edit="emit('edit', field)"
          @toggle-config="(f, k, v) => emit('toggleConfig', f, k, v)"
          @toggle-keep-on-archive="(f, v) => emit('toggleKeepOnArchive', f, v)"
          @toggle-required="(f, v) => emit('toggleRequired', f, v)"
          @toggle-readonly="(f, v) => emit('toggleReadonly', f, v)"
          @set-writability="(f, level) => emit('setWritability', f, level)"/>
    </template>
  </div>
</template>
