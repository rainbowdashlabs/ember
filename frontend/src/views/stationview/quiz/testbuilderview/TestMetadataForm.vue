/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import DateTimeInput from '@/components/input/datetime/DateTimeInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'

const title = defineModel<string>('title', { required: true })
const description = defineModel<string>('description', { required: true })
const startAt = defineModel<string>('startAt', { required: true })
const endAt = defineModel<string>('endAt', { required: true })
const shuffle = defineModel<boolean>('shuffle', { required: true })
const timeLimitEnabled = defineModel<boolean>('timeLimitEnabled', { required: true })
const timeLimit = defineModel<number | undefined>('timeLimit')

const { t } = useI18n()
</script>

<template>
  <NeutralContainer>
    <div class="space-y-4">
      <TextInput v-model="title" :placeholder="t('quiz.tests.titlePlaceholder')" />
      <TextAreaInput v-model="description" :placeholder="t('quiz.tests.descriptionPlaceholder')" />

      <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <div>
          <FieldLabel hint class="mb-1">{{ t('quiz.tests.startAt') }}</FieldLabel>
          <DateTimeInput v-model="startAt" />
        </div>
        <div>
          <FieldLabel hint class="mb-1">{{ t('quiz.tests.endAt') }}</FieldLabel>
          <DateTimeInput v-model="endAt" />
        </div>
      </div>

      <div class="flex flex-wrap gap-6">
        <FieldLabel inline>
          <ToggleInput v-model="shuffle" />
          {{ t('quiz.tests.shuffle') }}
        </FieldLabel>
        <FieldLabel inline>
          <ToggleInput v-model="timeLimitEnabled" />
          {{ t('quiz.tests.timeLimitEnabled') }}
        </FieldLabel>
      </div>

      <div v-if="timeLimitEnabled" class="flex items-center gap-2">
        <label class="text-sm text-(--text-muted)">{{ t('quiz.tests.timeLimitMinutes') }}</label>
        <NumberInput v-model="timeLimit" class="w-24" />
      </div>
    </div>
  </NeutralContainer>
</template>
