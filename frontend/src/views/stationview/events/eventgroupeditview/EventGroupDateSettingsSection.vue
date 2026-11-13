/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref, watch} from 'vue'
import {attendance} from '@/api'
import type {AttendanceTemplateField} from '@/api/generated/schema'
import {useEventEditDeps} from '@/composables/useEventEditDeps'
import EventGroupDateSettingsCard from './EventGroupDateSettingsCard.vue'
import type {useEventGroupSettings} from './useEventGroupSettings'

/**
 * The date settings card of the event group editor with the choices it offers: the station's
 * categories and attendance templates, and the fields of the chosen template its pre-fills are for.
 * Changing the template drops the pre-fills of the fields the new one does not have.
 */
const props = defineProps<{
  settings: ReturnType<typeof useEventGroupSettings>
}>()

const {categories, templates} = useEventEditDeps()
const templateFields = ref<AttendanceTemplateField[]>([])

watch(props.settings.templateId, async id => {
  templateFields.value = id ? await attendance.listTemplateFields(Number(id)).catch(() => []) : []
  props.settings.fieldDefaults.keepOnly(templateFields.value.map(field => field.id))
}, {immediate: true})
</script>

<template>
  <EventGroupDateSettingsCard
      :category-id="props.settings.categoryId.value"
      :template-id="props.settings.templateId.value"
      :reminders="props.settings.reminders.value"
      :categories="categories"
      :templates="templates"
      :template-fields="templateFields"
      :field-defaults="props.settings.fieldDefaults.defaults.value"
      @update:category-id="props.settings.setCategory"
      @update:template-id="props.settings.setTemplate"
      @update:reminders="props.settings.setReminders"
      @update:field-default-source="props.settings.fieldDefaults.setSource"
      @update:field-default-value="props.settings.fieldDefaults.setValue"
  />
</template>
