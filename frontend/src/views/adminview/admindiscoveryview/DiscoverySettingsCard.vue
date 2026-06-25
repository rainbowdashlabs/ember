/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import InfoButton from '@/components/button/InfoButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import type {DiscoverySettings} from '@/api/discovery'

defineProps<{
  settings: DiscoverySettings
  modelEnabled: boolean
  modelDepth: number
  modelInterval: number
  save: () => Promise<void>
}>()

const emit = defineEmits<{
  'update:modelEnabled': [value: boolean]
  'update:modelDepth': [value: number]
  'update:modelInterval': [value: number]
  discoverNow: []
  seedFederation: []
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-3">
    <SubHeader>{{ t('adminDiscovery.settings') }}</SubHeader>
    <div class="space-y-3">
      <div class="flex items-center justify-between gap-3">
        <div>
          <FieldLabel>{{ t('adminDiscovery.enabled') }}</FieldLabel>
          <p class="text-xs text-(--text-muted)">{{ t('adminDiscovery.enabledHelp') }}</p>
        </div>
        <ToggleInput :model-value="modelEnabled" @update:model-value="(v) => emit('update:modelEnabled', v)"/>
      </div>
      <div>
        <FieldLabel>{{ t('adminDiscovery.maxDepth') }}</FieldLabel>
        <p class="text-xs text-(--text-muted)">{{ t('adminDiscovery.maxDepthHelp') }}</p>
        <NumberInput :model-value="modelDepth" :min="0" :max="settings.hardMaxDepth"
                     @update:model-value="(v) => emit('update:modelDepth', v)"/>
      </div>
      <div>
        <FieldLabel>{{ t('adminDiscovery.pingInterval') }}</FieldLabel>
        <p class="text-xs text-(--text-muted)">{{ t('adminDiscovery.pingIntervalHelp') }}</p>
        <NumberInput :model-value="modelInterval" :min="60"
                     @update:model-value="(v) => emit('update:modelInterval', v)"/>
      </div>
      <div class="flex flex-wrap gap-2 pt-2">
        <SaveButton :action="save"/>
        <InfoButton @click="emit('discoverNow')">{{ t('adminDiscovery.discoverNow') }}</InfoButton>
        <SecondaryButton @click="emit('seedFederation')">{{ t('adminDiscovery.seedFederation') }}</SecondaryButton>
      </div>
    </div>
  </NeutralContainer>
</template>
