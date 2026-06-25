/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import type {TwoFactorPolicy} from '@/api/twoFactorAdmin'

const props = defineProps<{
  userTypes: string[]
  policyByUserType: Map<string, TwoFactorPolicy>
  saving: string | null
  userTypeLabel: (name: string) => string
}>()

const emit = defineEmits<{
  (e: 'toggle', userType: string): void
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-3">
    <SubHeader>{{ t('twoFactor.admin.policiesTitle') }}</SubHeader>
    <MutedText tag="p" size="sm">{{ t('twoFactor.admin.policiesHint') }}</MutedText>
    <ul class="space-y-2">
      <li v-for="ut in props.userTypes" :key="ut"
          class="flex items-center justify-between rounded border border-(--border) px-3 py-2">
        <div>
          <div class="text-sm font-medium">{{ props.userTypeLabel(ut) }}</div>
          <MutedText tag="div" size="sm">
            {{ props.policyByUserType.get(ut)?.required ? t('twoFactor.admin.required') : t('twoFactor.admin.optional') }}
          </MutedText>
        </div>
        <ToggleInput
            :model-value="!!props.policyByUserType.get(ut)?.required"
            :disabled="props.saving === ut"
            @update:model-value="emit('toggle', ut)"
        />
      </li>
    </ul>
  </NeutralContainer>
</template>
