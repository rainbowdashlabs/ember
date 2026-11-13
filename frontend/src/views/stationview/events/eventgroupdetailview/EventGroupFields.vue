/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import DetailLabel from '@/components/typography/DetailLabel.vue'
import QuestionValueDisplay from '@/components/display/QuestionValueDisplay.vue'
import type {AppointmentField} from '@/api/generated/schema'

/** The fields an event group shows for all its appointments, the empty ones left out. */
const props = defineProps<{
  fields: AppointmentField[]
}>()

const shown = computed(() => props.fields.filter(field => (field.value ?? '').trim() !== ''))
</script>

<template>
  <div v-if="shown.length > 0" class="grid grid-cols-1 sm:grid-cols-2 gap-3" data-testid="event-group-field-values">
    <div v-for="field in shown" :key="field.id">
      <DetailLabel>{{ field.name }}</DetailLabel>
      <p class="text-sm"><QuestionValueDisplay :field-type="field.fieldType" :value="field.value"/></p>
    </div>
  </div>
</template>
