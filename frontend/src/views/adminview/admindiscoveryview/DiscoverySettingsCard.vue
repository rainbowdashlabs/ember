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
  save: () => Promise<void>
}>()

const modelEnabled = defineModel<boolean>('modelEnabled', {required: true})
const modelDepth = defineModel<number>('modelDepth', {required: true})
const modelInterval = defineModel<number>('modelInterval', {required: true})

const emit = defineEmits<{
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
        <ToggleInput v-model="modelEnabled"/>
      </div>
      <div>
        <FieldLabel>{{ t('adminDiscovery.maxDepth') }}</FieldLabel>
        <p class="text-xs text-(--text-muted)">{{ t('adminDiscovery.maxDepthHelp') }}</p>
        <NumberInput v-model="modelDepth" :min="0" :max="settings.hardMaxDepth"/>
      </div>
      <div>
        <FieldLabel>{{ t('adminDiscovery.pingInterval') }}</FieldLabel>
        <p class="text-xs text-(--text-muted)">{{ t('adminDiscovery.pingIntervalHelp') }}</p>
        <NumberInput v-model="modelInterval" :min="60"/>
      </div>
      <div class="flex flex-wrap gap-2 pt-2">
        <SaveButton :action="save"/>
        <InfoButton @click="emit('discoverNow')">{{ t('adminDiscovery.discoverNow') }}</InfoButton>
        <SecondaryButton @click="emit('seedFederation')">{{ t('adminDiscovery.seedFederation') }}</SecondaryButton>
      </div>
    </div>
  </NeutralContainer>
</template>
