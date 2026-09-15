/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import DesktopHeaderRow from './fieldtable/DesktopHeaderRow.vue'
import DesktopFieldRow from './fieldtable/DesktopFieldRow.vue'
import MobileFieldCard from './fieldtable/MobileFieldCard.vue'
import {type ProfileField} from '@/api/profileFields'
import {useBreakpoint} from '@/composables/useBreakpoint'
import {useI18n} from 'vue-i18n'
import {fieldTypeLabel} from './fieldTypes'
import type {WritabilityName} from '@/composables/useFieldsConfig'

/**
 * The questions a station asks, each written once and listed by name.
 *
 * <p>Nothing is dragged here. The order a question stands in belongs to the form it stands on, and one
 * question stands on several, so ordering is done on the form rather than on the list of questions.
 *
 * @param audienceCount how many audiences each question is put to, by question id
 */
const props = defineProps<{
  fields: ProfileField[]
  selectedId: number | null
  audienceCount: Record<number, number>
  /** The questions ticked for putting to somebody all at once. */
  checkedIds: Set<number>
}>()

const {isMobile} = useBreakpoint()

const emit = defineEmits<{
  select: [field: ProfileField]
  toggleChecked: [field: ProfileField]
  edit: [field: ProfileField]
  delete: [field: ProfileField]
  toggleConfig: [field: ProfileField, key: string, value: boolean]
  toggleKeepOnArchive: [field: ProfileField, value: boolean]
  toggleRequired: [field: ProfileField, value: boolean]
  toggleReadonly: [field: ProfileField, value: boolean]
  setWritability: [field: ProfileField, level: WritabilityName]
}>()

const {t} = useI18n()

function typeLabel(value: string): string {
  return fieldTypeLabel(t, value)
}

function audiencesOf(field: ProfileField): number {
  return props.audienceCount[field.id] ?? 0
}
</script>

<template>
  <div v-if="isMobile">
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
  </div>

  <div v-else>
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
  </div>
</template>
