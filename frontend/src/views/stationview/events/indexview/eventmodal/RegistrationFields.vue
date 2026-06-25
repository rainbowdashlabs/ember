/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import DateTimeInput from '@/components/input/datetime/DateTimeInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'

const {t} = useI18n()

const eventRequiresRegistration = defineModel<boolean>('eventRequiresRegistration', {required: true})
const eventRequiresConfirmation = defineModel<boolean>('eventRequiresConfirmation', {required: true})
const eventHasDeadline = defineModel<boolean>('eventHasDeadline', {required: true})
const eventRegistrationDeadline = defineModel<string>('eventRegistrationDeadline', {required: true})
</script>

<template>
  <div class="space-y-4">
    <div class="flex items-center justify-between">
      <label class="text-sm font-medium">{{ t('events.requiresRegistration') }}</label>
      <ToggleInput v-model="eventRequiresRegistration" />
    </div>

    <template v-if="eventRequiresRegistration">
      <div class="flex items-center justify-between">
        <label class="text-sm font-medium">{{ t('events.requiresConfirmation') }}</label>
        <ToggleInput v-model="eventRequiresConfirmation" />
      </div>
      <p class="text-xs text-(--text-muted)">{{ t('events.requiresConfirmationHint') }}</p>

      <div class="flex items-center justify-between">
        <label class="text-sm font-medium">{{ t('events.hasDeadline') }}</label>
        <ToggleInput v-model="eventHasDeadline" />
      </div>

      <div v-if="eventHasDeadline" class="space-y-1">
        <FieldLabel>{{ t('events.registrationDeadline') }}</FieldLabel>
        <DateTimeInput v-model="eventRegistrationDeadline" />
      </div>
    </template>
  </div>
</template>
