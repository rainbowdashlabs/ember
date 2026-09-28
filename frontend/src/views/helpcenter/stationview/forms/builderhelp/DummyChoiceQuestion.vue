/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ChipButton from '@/components/button/ChipButton.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import IconButton from '@/components/button/IconButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import AppIcon from '@/components/display/AppIcon.vue'
import FieldHint from '@/components/typography/FieldHint.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SectionLabel from '@/components/typography/SectionLabel.vue'

const {t} = useI18n()
</script>

<template>
  <NeutralContainer>
    <div class="space-y-3">
      <div class="flex items-center justify-between">
        <div class="flex items-center gap-2">
          <AppIcon :icon="['fas', 'grip-vertical']" class="text-(--text-muted)"/>
          <SectionLabel>2. {{ t('forms.questionTypes.CHOICE') }}</SectionLabel>
        </div>
        <IconButton :icon="['fas', 'ellipsis']" :label="t('forms.questionMenu.open')" class="text-(--text-muted)" disabled/>
      </div>
      <TextInput model-value="Was gefällt dir am besten?" disabled />
      <div class="flex flex-wrap gap-1.5">
        <ChipButton disabled>{{ t('forms.chips.multiSelectLimited', {limit: t('forms.chips.limit.AT_MOST', {count: 2})}) }}</ChipButton>
        <ChipButton disabled>{{ t('forms.chips.allowOther') }}</ChipButton>
      </div>
      <div class="space-y-1 opacity-60">
        <FieldHint>{{ t('forms.choice.options') }}</FieldHint>
        <div v-for="opt in ['Übungen', 'Gemeinschaft']" :key="opt" class="flex gap-2 items-center">
          <TextInput :model-value="opt" disabled class="flex-1" />
          <DeleteButton disabled />
        </div>
        <SecondaryButton disabled>{{ t('forms.choice.addOption') }}</SecondaryButton>
      </div>
      <FieldLabel inline>
        <ToggleInput :model-value="true" disabled />
        {{ t('forms.questionRequired') }}
      </FieldLabel>
    </div>
  </NeutralContainer>
</template>
