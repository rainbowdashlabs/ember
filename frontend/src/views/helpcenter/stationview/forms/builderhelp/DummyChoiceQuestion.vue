/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import IconButton from '@/components/button/IconButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import FieldHint from '@/components/typography/FieldHint.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SectionLabel from '@/components/typography/SectionLabel.vue'

const {t} = useI18n()
</script>

<template>
  <NeutralContainer>
    <div class="space-y-3">
      <div class="flex items-center justify-between">
        <SectionLabel>2. {{ t('forms.questionTypes.CHOICE') }}</SectionLabel>
        <div class="flex gap-1">
          <IconButton :icon="['fas', 'chevron-up']" label="Up" class="text-(--text-muted)" disabled />
          <IconButton :icon="['fas', 'chevron-down']" label="Down" class="text-(--text-muted)" disabled />
          <DeleteButton disabled />
        </div>
      </div>
      <TextInput model-value="Was gefällt dir am besten?" disabled />
      <div class="flex gap-4 flex-wrap opacity-60">
        <FieldLabel inline>
          <ToggleInput :model-value="true" disabled />
          {{ t('forms.choice.multiSelect') }}
        </FieldLabel>
        <FieldLabel inline>
          <ToggleInput :model-value="false" disabled />
          {{ t('forms.choice.dropdown') }}
        </FieldLabel>
        <FieldLabel inline>
          <ToggleInput :model-value="true" disabled />
          {{ t('forms.choice.allowOther') }}
        </FieldLabel>
      </div>
      <div class="space-y-1 opacity-60">
        <FieldHint>{{ t('forms.choice.options') }}</FieldHint>
        <div v-for="opt in ['Übungen', 'Gemeinschaft']" :key="opt" class="flex gap-2 items-center">
          <TextInput :model-value="opt" disabled class="flex-1" />
          <IconButton :icon="['fas', 'chevron-up']" label="Up" class="text-(--text-muted)" disabled />
          <IconButton :icon="['fas', 'chevron-down']" label="Down" class="text-(--text-muted)" disabled />
          <DeleteButton disabled />
        </div>
        <SecondaryButton disabled>{{ t('forms.choice.addOption') }}</SecondaryButton>
      </div>
    </div>
  </NeutralContainer>
</template>
