/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'

const {t} = useI18n()

defineProps<{
  registrationEnabled: boolean
}>()

const forcePrideFlag = defineModel<boolean>('forcePrideFlag', {required: true})

const emit = defineEmits<{
  'toggle-registration': [value: boolean]
  'save-pride': []
}>()

function onPrideToggle(value: boolean) {
  forcePrideFlag.value = value
  emit('save-pride')
}
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SectionHeader>{{ t('adminSettings.title') }}</SectionHeader>

    <div class="flex items-center justify-between">
      <div>
        <div class="font-medium">{{ t('adminSettings.stationRegistration') }}</div>
        <div class="text-sm text-(--text-muted)">{{ t('adminSettings.stationRegistrationHint') }}</div>
      </div>
      <ToggleInput :model-value="registrationEnabled"
                   @update:model-value="(v: boolean) => emit('toggle-registration', v)"/>
    </div>

    <div class="flex items-center justify-between">
      <div>
        <div class="font-medium">{{ t('adminSettings.forcePrideFlag') }}</div>
        <div class="text-sm text-(--text-muted)">{{ t('adminSettings.forcePrideFlagHint') }}</div>
      </div>
      <ToggleInput :model-value="forcePrideFlag" @update:model-value="onPrideToggle"/>
    </div>
  </NeutralContainer>
</template>
