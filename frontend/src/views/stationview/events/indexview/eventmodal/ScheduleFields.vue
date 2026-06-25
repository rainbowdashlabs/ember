/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import DateTimeInput from '@/components/input/datetime/DateTimeInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'

const {t} = useI18n()

const eventStartTime = defineModel<string>('eventStartTime', {required: true})
const eventEndTime = defineModel<string>('eventEndTime', {required: true})
</script>

<template>
  <div class="grid grid-cols-2 gap-3">
    <div class="space-y-1">
      <FieldLabel>{{ t('events.startTime') }}</FieldLabel>
      <DateTimeInput v-model="eventStartTime" />
    </div>
    <div class="space-y-1">
      <FieldLabel>{{ t('events.endTime') }}</FieldLabel>
      <DateTimeInput v-model="eventEndTime" />
    </div>
  </div>
</template>
