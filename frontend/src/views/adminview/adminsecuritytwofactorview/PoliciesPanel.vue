/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import Alert from '@/components/feedback/Alert.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import {twoFactorAdmin} from '@/api'
import type {TwoFactorPolicy} from '@/api/twoFactorAdmin'
import {useConfigPanel} from '@/composables/useConfigPanel'

const {t} = useI18n()
const USER_TYPES = ['MEMBER', 'GUARDIAN', 'TEAM', 'MANAGER'] as const

const saving = ref<string | null>(null)

const {config: policies, loading, error, runWith} = useConfigPanel<TwoFactorPolicy[]>({
  initial: [],
  fetch: () => twoFactorAdmin.listInstancePolicies(),
  formatError: (e: any) => e?.response?.data?.message || t('common.error'),
})

const policyByUserType = computed(() => {
  const map = new Map<string, TwoFactorPolicy>()
  for (const p of policies.value) {
    if (p.userType) map.set(p.userType, p)
  }
  return map
})

async function togglePolicy(userType: string) {
  const existing = policyByUserType.value.get(userType)
  saving.value = userType
  await runWith(async () => {
    if (existing && existing.required) {
      await twoFactorAdmin.deleteInstancePolicy(existing.id)
    } else {
      await twoFactorAdmin.upsertInstancePolicy(userType, true)
    }
    return twoFactorAdmin.listInstancePolicies()
  })
  saving.value = null
}

function userTypeLabel(name: string): string {
  const key = `twoFactor.admin.userTypes.${name}`
  const translated = t(key)
  return translated === key ? name : translated
}
</script>

<template>
  <NeutralContainer class="space-y-3">
    <SubHeader>{{ t('twoFactor.admin.policiesTitle') }}</SubHeader>
    <Spinner v-if="loading" size="sm"/>
    <Alert v-if="error" variant="error">{{ error }}</Alert>
    <ul v-if="!loading" class="space-y-2">
      <li v-for="ut in USER_TYPES" :key="ut"
          class="flex items-center justify-between rounded border border-(--border) px-3 py-2">
        <div>
          <div class="text-sm font-medium">{{ userTypeLabel(ut) }}</div>
          <MutedText tag="div" size="sm">
            {{ policyByUserType.get(ut)?.required ? t('twoFactor.admin.required') : t('twoFactor.admin.optional') }}
          </MutedText>
        </div>
        <ToggleInput
            :model-value="!!policyByUserType.get(ut)?.required"
            :disabled="saving === ut"
            @update:model-value="togglePolicy(ut)"
        />
      </li>
    </ul>
  </NeutralContainer>
</template>
