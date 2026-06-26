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
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import DateTimeInput from '@/components/input/datetime/DateTimeInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'

const { t } = useI18n()

const title = defineModel<string>('title', { required: true })
const description = defineModel<string>('description', { required: true })
const startAt = defineModel<string>('startAt', { required: true })
const endAt = defineModel<string>('endAt', { required: true })
const shuffleQuestions = defineModel<boolean>('shuffleQuestions', { required: true })
const allowEdit = defineModel<boolean>('allowEdit', { required: true })
const forced = defineModel<boolean>('forced', { required: true })
</script>

<template>
  <NeutralContainer>
    <div class="space-y-4">
      <TextInput v-model="title" :placeholder="t('forms.title')" />
      <TextAreaInput v-model="description" :placeholder="t('forms.description')" />
      <div class="grid grid-cols-2 gap-4">
        <div>
          <FieldLabel hint class="mb-1">{{ t('forms.startAt') }}</FieldLabel>
          <DateTimeInput v-model="startAt" />
        </div>
        <div>
          <FieldLabel hint class="mb-1">{{ t('forms.endAt') }}</FieldLabel>
          <DateTimeInput v-model="endAt" />
        </div>
      </div>
      <div class="flex gap-6 flex-wrap">
        <FieldLabel inline>
          <ToggleInput v-model="shuffleQuestions" />
          {{ t('forms.shuffleQuestions') }}
        </FieldLabel>
        <FieldLabel inline>
          <ToggleInput v-model="allowEdit" />
          {{ t('forms.allowEdit') }}
        </FieldLabel>
        <FieldLabel inline>
          <ToggleInput v-model="forced" />
          {{ t('forms.forced') }}
        </FieldLabel>
      </div>
    </div>
  </NeutralContainer>
</template>
