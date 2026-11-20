/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import DetailLabel from '@/components/typography/DetailLabel.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import type {AttendanceTemplate, EventCategory, EventGroupSettings} from '@/api/generated/schema'

/** Which of the date settings an event group hands down a row is about. */
export type InheritedSetting = 'category' | 'template' | 'reminders'

interface Row {
  setting: InheritedSetting
  label: string
  value: string
  replaced: boolean
}

/**
 * The date settings an appointment takes over from its event group, read-only here: the category, the
 * attendance template with its pre-fills, and the reminders. "Override" copies one into the
 * appointment's own settings, which then replace the event group's for this appointment; removing the
 * own setting again brings the event group's back. Settings the event group leaves empty are not listed.
 */
const props = defineProps<{
  settings: EventGroupSettings
  categories: EventCategory[]
  templates: AttendanceTemplate[]
  /** The appointment's own category as the editor holds it, empty where it follows the event group. */
  ownCategoryId: string
  /** The appointment's own attendance template as the editor holds it, empty where it follows. */
  ownTemplateId: string
  /** The appointment's own reminders as the editor holds them, none where it follows. */
  ownReminders: number[]
}>()

const emit = defineEmits<{
  override: [setting: InheritedSetting]
}>()

const {t} = useI18n()

function nameOf(list: {id: number; name: string}[], id: number): string {
  return list.find(entry => entry.id === id)?.name ?? `#${id}`
}

const rows = computed<Row[]>(() => {
  const found: Row[] = []
  const {categoryId, templateId, reminderDays, fieldDefaults} = props.settings
  if (categoryId != null) {
    found.push({
      setting: 'category',
      label: t('events.category'),
      value: nameOf(props.categories, categoryId),
      replaced: props.ownCategoryId !== '',
    })
  }
  if (templateId != null) {
    const name = nameOf(props.templates, templateId)
    found.push({
      setting: 'template',
      label: t('events.template'),
      value: fieldDefaults.length > 0 ? t('eventGroups.templateWithDefaults', {name, count: fieldDefaults.length}) : name,
      replaced: props.ownTemplateId !== '',
    })
  }
  if (reminderDays.length > 0) {
    found.push({
      setting: 'reminders',
      label: t('eventEdit.reminders'),
      value: t('eventGroups.reminderDays', {days: reminderDays.join(', ')}),
      replaced: props.ownReminders.length > 0,
    })
  }
  return found
})
</script>

<template>
  <NeutralContainer v-if="rows.length > 0" class="space-y-3" data-testid="inherited-settings">
    <SectionHeader>{{ t('eventGroups.inheritedSettings') }}</SectionHeader>
    <MutedText tag="p" size="sm">{{ t('eventGroups.inheritedSettingsHint') }}</MutedText>
    <div v-for="row in rows" :key="row.setting" class="flex flex-wrap items-center justify-between gap-2"
         data-testid="inherited-setting">
      <div class="min-w-0">
        <DetailLabel>{{ row.label }}</DetailLabel>
        <p class="text-sm" :class="{'line-through text-(--text-muted)': row.replaced}">{{ row.value }}</p>
      </div>
      <MutedText v-if="row.replaced" tag="span" size="sm">{{ t('eventGroups.overridden') }}</MutedText>
      <SecondaryButton v-else :icon="['fas', 'pen']" compact data-testid="override-setting" :data-setting="row.setting"
                       @click="emit('override', row.setting)">
        {{ t('eventGroups.override') }}
      </SecondaryButton>
    </div>
  </NeutralContainer>
</template>
