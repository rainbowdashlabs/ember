/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {EventFieldEntry} from '@/api/generated/schema'
import {emptySettings} from './eventQuestions'

/**
 * What only the registrant's question has: whether the answer shows in the registration list, and
 * whether it is kept for whoever runs the appointment.
 */
const question = defineModel<EventFieldEntry>({required: true})

const {t} = useI18n()

const settings = computed(() => question.value.config ?? emptySettings())
</script>

<template>
  <div class="space-y-1">
    <FieldLabel>{{ t('events.registrationFields.description') }}</FieldLabel>
    <TextInput :model-value="settings.description ?? ''" data-testid="registration-question-description"
               :placeholder="t('events.registrationFields.descriptionPlaceholder')"
               @update:model-value="description => question = {...question, config: {...settings, description: String(description ?? '') || undefined}}"/>
    <MutedText tag="p" class="text-xs">{{ t('events.registrationFields.descriptionHint') }}</MutedText>
  </div>
  <div class="flex flex-wrap gap-6">
    <label class="flex items-center gap-2 text-sm">
      <ToggleInput :model-value="question.overview ?? false"
                   @update:model-value="overview => question = {...question, overview}"/>
      {{ t('events.registrationFields.overview') }}
    </label>
    <label class="flex items-center gap-2 text-sm" :title="t('events.registrationFields.managersOnlyHint')">
      <ToggleInput :model-value="settings.managersOnly"
                   @update:model-value="managersOnly => question = {...question, config: {...settings, managersOnly}}"/>
      {{ t('events.registrationFields.managersOnly') }}
    </label>
  </div>
</template>
