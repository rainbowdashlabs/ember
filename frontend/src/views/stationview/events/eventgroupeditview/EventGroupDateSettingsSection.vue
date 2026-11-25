/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useEventEditDeps} from '@/composables/useEventEditDeps'
import EventGroupDateSettingsCard from './EventGroupDateSettingsCard.vue'
import type {useEventGroupSettings} from './useEventGroupSettings'

/**
 * The date settings card of the event group editor with the choices it offers: the station's
 * categories and attendance templates, and the fields of the chosen template its pre-fills are for.
 */
const props = defineProps<{
  settings: ReturnType<typeof useEventGroupSettings>
  /** The form's compact card: category and template in one row, reminders asked elsewhere. */
  compact?: boolean
}>()

const {categories, templates} = useEventEditDeps()
</script>

<template>
  <EventGroupDateSettingsCard
      :category-id="props.settings.categoryId.value"
      :template-id="props.settings.templateId.value"
      :reminders="props.settings.reminders.value"
      :categories="categories"
      :templates="templates"
      :template-fields="props.settings.templateFields.value"
      :field-defaults="props.settings.fieldDefaults.defaults.value"
      :compact="compact"
      @update:category-id="props.settings.setCategory"
      @update:template-id="props.settings.setTemplate"
      @update:reminders="props.settings.setReminders"
      @update:field-default-source="props.settings.fieldDefaults.setSource"
      @update:field-default-value="props.settings.fieldDefaults.setValue"
  >
    <template #row-end>
      <slot name="row-end"/>
    </template>
    <template #after-row>
      <slot name="after-row"/>
    </template>
  </EventGroupDateSettingsCard>
</template>
