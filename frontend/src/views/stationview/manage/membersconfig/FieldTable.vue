/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import MutedText from '@/components/typography/MutedText.vue'
import DesktopHeaderRow from './fieldtable/DesktopHeaderRow.vue'
import DesktopFieldRow from './fieldtable/DesktopFieldRow.vue'
import MobileFieldCard from './fieldtable/MobileFieldCard.vue'
import type {EditableField, FieldSwitchName} from '@/api/profileFields'
import type {FieldSet} from '@/api/generated/schema'
import {useBreakpoint} from '@/composables/useBreakpoint'
import {useElementWidth} from '@/composables/useElementWidth'
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {fieldTypeLabel} from '@/api/fieldTypes'
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
  /** The owner's groups of fields, by which each field in one is labelled. */
  sets?: FieldSet[]
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

/** One run of rows: a group with its questions in the group's order, or questions on their own. */
interface Block {
  key: string
  set: FieldSet | null
  fields: EditableField[]
}

/**
 * The questions as the table shows them: each group as one framed block, where its first question would
 * have stood, so what belongs together is read off the table at a glance.
 */
const blocks = computed<Block[]>(() => {
  const result: Block[] = []
  const placed = new Set<number>()
  for (const field of props.fields) {
    const set = field.fieldSetId == null ? undefined : props.sets?.find(s => s.id === field.fieldSetId)
    if (!set) {
      const last = result[result.length - 1]
      if (last && !last.set) last.fields.push(field)
      else result.push({key: `loose-${field.id}`, set: null, fields: [field]})
      continue
    }
    if (placed.has(set.id)) continue
    placed.add(set.id)
    result.push({
      key: `set-${set.id}`,
      set,
      fields: props.fields
          .filter(member => member.fieldSetId === set.id)
          .sort((a, b) => (a.setPosition ?? 0) - (b.setPosition ?? 0)),
    })
  }
  return result
})

const row = computed(() => tiled.value ? MobileFieldCard : DesktopFieldRow)

function rowProps(field: EditableField) {
  return {
    field,
    audiences: audiencesOf(field),
    selected: field.id === props.selectedId,
    checked: props.checkedIds.has(field.id),
    typeLabel: typeLabel(field.fieldType ?? ''),
    shownIfName: shownIfNameOf(field),
  }
}

const rowEvents = {
  select: (f: EditableField) => emit('select', f),
  toggleChecked: (f: EditableField) => emit('toggleChecked', f),
  delete: (f: EditableField) => emit('delete', f),
  edit: (f: EditableField) => emit('edit', f),
  toggleConfig: (f: EditableField, k: FieldSwitchName, v: boolean) => emit('toggleConfig', f, k, v),
  toggleKeepOnArchive: (f: EditableField, v: boolean) => emit('toggleKeepOnArchive', f, v),
  toggleRequired: (f: EditableField, v: boolean) => emit('toggleRequired', f, v),
  toggleReadonly: (f: EditableField, v: boolean) => emit('toggleReadonly', f, v),
  setWritability: (f: EditableField, level: WritabilityName) => emit('setWritability', f, level),
}

/** The name of the yes/no question a question is only shown behind, or nothing. */
function shownIfNameOf(field: EditableField): string | undefined {
  if (field.shownIfFieldId == null) return undefined
  return props.fields.find(other => other.id === field.shownIfFieldId)?.name
}
</script>

<template>
  <div ref="table">
    <DesktopHeaderRow v-if="!tiled"/>
    <template v-for="block in blocks" :key="block.key">
      <div v-if="block.set" class="my-2 rounded-theme border-l-4 border-secondary bg-secondary/5"
           :data-testid="`field-table-set-${block.set.name}`">
        <div class="flex items-center gap-2 px-3 pt-2 pb-1 text-xs font-semibold">
          <font-awesome-icon :icon="['fas', 'layer-group']" class="h-3 w-3"/>
          <span>{{ block.set.name }}</span>
          <MutedText>{{ t('membersConfig.sets.fieldCount', {n: block.fields.length}) }}</MutedText>
        </div>
        <component :is="row" v-for="field in block.fields" :key="field.id" v-bind="rowProps(field)" v-on="rowEvents"/>
      </div>
      <component :is="row" v-for="field in block.fields" v-else :key="field.id" v-bind="rowProps(field)"
                 v-on="rowEvents"/>
    </template>
  </div>
</template>
