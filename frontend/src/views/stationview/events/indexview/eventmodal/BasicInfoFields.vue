/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import {EventTypes} from '@/api/events'

const {t} = useI18n()

const eventName = defineModel<string>('eventName', {required: true})
const eventDescription = defineModel<string>('eventDescription', {required: true})
const eventType = defineModel<string>('eventType', {required: true})
const eventDayOfWeek = defineModel<string>('eventDayOfWeek', {required: true})
</script>

<template>
  <div class="space-y-4">
    <div class="space-y-1">
      <FieldLabel>{{ t('events.name') }}</FieldLabel>
      <TextInput v-model="eventName" :placeholder="t('events.namePlaceholder')" />
    </div>

    <div class="space-y-1">
      <FieldLabel>{{ t('events.description') }}</FieldLabel>
      <TextInput v-model="eventDescription" :placeholder="t('events.descriptionPlaceholder')" />
    </div>

    <div class="space-y-1">
      <FieldLabel>{{ t('events.type') }}</FieldLabel>
      <SelectInput v-model="eventType">
        <option :value="EventTypes.RECURRING">{{ t('events.typeRecurring') }}</option>
        <option :value="EventTypes.ONE_TIME">{{ t('events.typeOneTime') }}</option>
      </SelectInput>
    </div>

    <div v-if="eventType === EventTypes.RECURRING" class="space-y-1">
      <FieldLabel>{{ t('events.dayOfWeek') }}</FieldLabel>
      <SelectInput v-model="eventDayOfWeek">
        <option value="1">Montag</option>
        <option value="2">Dienstag</option>
        <option value="3">Mittwoch</option>
        <option value="4">Donnerstag</option>
        <option value="5">Freitag</option>
        <option value="6">Samstag</option>
        <option value="7">Sonntag</option>
      </SelectInput>
    </div>
  </div>
</template>
