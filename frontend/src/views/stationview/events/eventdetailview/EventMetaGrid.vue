/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import DetailLabel from '@/components/typography/DetailLabel.vue'
import EventFieldValue from '@/components/display/EventFieldValue.vue'
import type {EventField} from '@/api/types'

defineProps<{
  fields: EventField[]
  startFormatted: string
  endFormatted: string
  categoryName: string
  templateName: string
  canManageEvents: boolean
}>()

const {t} = useI18n()
</script>

<template>
  <div class="grid gap-4 sm:grid-cols-2">
    <div>
      <DetailLabel>{{ t('events.category') }}</DetailLabel>
      <p class="text-sm">{{ categoryName }}</p>
    </div>
    <div>
      <DetailLabel>{{ t('events.startTime') }}</DetailLabel>
      <p class="text-sm">{{ startFormatted }}</p>
    </div>
    <div>
      <DetailLabel>{{ t('events.endTime') }}</DetailLabel>
      <p class="text-sm">{{ endFormatted }}</p>
    </div>
    <div v-if="canManageEvents">
      <DetailLabel>{{ t('events.template') }}</DetailLabel>
      <p class="text-sm">{{ templateName }}</p>
    </div>
    <div v-for="field in fields" :key="field.id">
      <DetailLabel>{{ field.name }}</DetailLabel>
      <p class="text-sm"><EventFieldValue :field-type="field.fieldType" :value="field.value"/></p>
    </div>
  </div>
</template>
