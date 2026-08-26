/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import DragList from '@/components/input/DragList.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import type {AttendanceTemplateField} from '@/api/attendance'
import type {MemberGroup} from '@/api/types'
import MutedText from '@/components/typography/MutedText.vue'
import FieldLayoutPreview from '@/components/profilefields/FieldLayoutPreview.vue'
import {configOf} from '@/components/profilefields/fieldLayout'

const props = defineProps<{
  fields: AttendanceTemplateField[]
  availableGroups: MemberGroup[]
}>()

/** The settings an attendance question carries, of which the list shows a few. */
interface AttendanceFieldConfig {
  groupId?: number
  required?: boolean
  autoAttend?: boolean
  width?: string
}

const emit = defineEmits<{
  add: []
  edit: [field: AttendanceTemplateField]
  delete: [field: AttendanceTemplateField]
  reorder: [fromIndex: number, toIndex: number]
}>()

const {t, te} = useI18n()

/**
 * What a field's kind is called, from the same list the form offers when one is chosen.
 *
 * <p>It read a second list of its own before, keyed in lower case while the kinds are stored in upper
 * case, so nothing ever matched and every row showed the raw value. Two lists of the same thing is how
 * that happened, so there is one now.
 */
function fieldTypeLabel(value: string): string {
  const key = `attendanceConfig.fieldTypeLabels.${value}`
  return te(key) ? t(key) : value
}

/** What a field's settings say, in the shape this list reads them in. */
function parseConfig(config: string | Record<string, unknown> | undefined): AttendanceFieldConfig {
  return configOf(config) as AttendanceFieldConfig
}

function groupName(groupId: number): string {
  return props.availableGroups.find(g => g.id === groupId)?.name ?? `#${groupId}`
}
</script>

<template>
  <NeutralContainer class="space-y-4" data-testid="attendance-fields">
    <div class="flex items-center justify-between">
      <SectionHeader>{{ t('attendanceConfig.fields') }}</SectionHeader>
      <PrimaryButton :icon="['fas', 'plus']" @click="emit('add')">
        {{ t('attendanceConfig.addField') }}
      </PrimaryButton>
    </div>

    <EmptyState v-if="fields.length === 0">{{ t('attendanceConfig.noFields') }}</EmptyState>

    <FieldLayoutPreview
        :fields="props.fields.map(field => ({name: field.name, width: parseConfig(field.config).width}))"
    />

    <DragList :items="fields" :key-fn="(f) => f.id" @reorder="(from, to) => emit('reorder', from, to)">
      <template #default="{ item: field }">
        <div
            class="flex items-center justify-between rounded-lg border p-4 border-bg-light-accent bg-bg-light-accent/20 dark:border-bg-dark-accent dark:bg-bg-dark-accent/20 mb-2"
            data-testid="attendance-field-row">
          <div class="flex items-center gap-2">
            <div>
              <span class="font-medium">{{ field.name }}</span>
              <span v-if="parseConfig(field.config).required" class="ml-1 text-xs text-error">*</span>
              <MutedText size="sm" class="ml-2">({{ fieldTypeLabel(field.fieldType ?? '') }})</MutedText>
              <MutedText class="ml-1" v-if="parseConfig(field.config).groupId">
                - {{ groupName(parseConfig(field.config).groupId!) }}
              </MutedText>
              <span v-if="parseConfig(field.config).autoAttend" class="ml-2 text-xs text-primary">
                <font-awesome-icon :icon="['fas', 'user-plus']" class="mr-0.5"/>auto
              </span>
            </div>
          </div>
          <div class="flex items-center gap-2">
            <EditButton @click="emit('edit', field)"/>
            <DeleteButton @click="emit('delete', field)"/>
          </div>
        </div>
      </template>
    </DragList>
  </NeutralContainer>
</template>
