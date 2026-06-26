/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-4 mt-4">
    <SectionHeader>{{ t('adminSettings.theme.title') }}</SectionHeader>
    <p class="text-sm text-(--text-muted)">{{ t('adminSettings.theme.hint') }}</p>

    <div class="space-y-1">
      <FieldLabel>{{ t('adminSettings.theme.defaultTheme') }}</FieldLabel>
      <SelectInput :model-value="'ember'" class="w-full">
        <option value="ember">Ember</option>
      </SelectInput>
    </div>

    <div class="space-y-1">
      <FieldLabel>{{ t('adminSettings.theme.defaultFeel') }}</FieldLabel>
      <SelectInput :model-value="'ROUNDED'" class="w-full">
        <option value="ROUNDED">{{ t('theme.feelROUNDED') }}</option>
        <option value="CORNERS">{{ t('theme.feelCORNERS') }}</option>
      </SelectInput>
    </div>

    <div class="flex items-center justify-between">
      <div>
        <div class="font-medium">{{ t('adminSettings.theme.lockFeel') }}</div>
        <div class="text-sm text-(--text-muted)">{{ t('adminSettings.theme.lockFeelHint') }}</div>
      </div>
      <ToggleInput :model-value="false" />
    </div>

    <div class="flex justify-end">
      <PrimaryButton>{{ t('adminSettings.legal.save') }}</PrimaryButton>
    </div>
  </NeutralContainer>
</template>
