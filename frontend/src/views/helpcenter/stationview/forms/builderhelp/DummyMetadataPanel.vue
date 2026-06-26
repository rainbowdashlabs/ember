/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import DateTimeInput from '@/components/input/datetime/DateTimeInput.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'

const {t} = useI18n()
</script>

<template>
  <NeutralContainer>
    <div class="space-y-3">
      <TextInput model-value="Zufriedenheitsumfrage" disabled />
      <TextAreaInput model-value="Wie gefällt dir unsere Jugendfeuerwehr?" disabled />
      <div class="grid grid-cols-2 gap-4">
        <div>
          <FieldLabel hint class="mb-1">{{ t('forms.startAt') }}</FieldLabel>
          <DateTimeInput disabled />
        </div>
        <div>
          <FieldLabel hint class="mb-1">{{ t('forms.endAt') }}</FieldLabel>
          <DateTimeInput disabled />
        </div>
      </div>
      <div class="flex gap-6">
        <FieldLabel inline class="opacity-60">
          <ToggleInput :model-value="false" disabled />
          {{ t('forms.shuffleQuestions') }}
        </FieldLabel>
        <FieldLabel inline class="opacity-60">
          <ToggleInput :model-value="true" disabled />
          {{ t('forms.allowEdit') }}
        </FieldLabel>
        <FieldLabel inline class="opacity-60">
          <ToggleInput :model-value="false" disabled />
          {{ t('forms.forced') }}
        </FieldLabel>
      </div>
    </div>
  </NeutralContainer>
</template>
