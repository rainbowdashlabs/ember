/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import Alert from '@/components/feedback/Alert.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import {twoFactorAdmin} from '@/api'
import type {TwoFactorPolicy} from '@/api/twoFactorAdmin'

const {t} = useI18n()
const USER_TYPES = ['MEMBER', 'GUARDIAN', 'TEAM', 'MANAGER'] as const

const loading = ref(true)
const error = ref('')
const policies = ref<TwoFactorPolicy[]>([])
const saving = ref<string | null>(null)

const policyByUserType = computed(() => {
  const map = new Map<string, TwoFactorPolicy>()
  for (const p of policies.value) {
    if (p.userType) map.set(p.userType, p)
  }
  return map
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    policies.value = await twoFactorAdmin.listInstancePolicies()
  } catch (e: any) {
    error.value = e?.response?.data?.message || t('common.error')
  }
  loading.value = false
}

async function togglePolicy(userType: string) {
  const existing = policyByUserType.value.get(userType)
  saving.value = userType
  try {
    if (existing && existing.required) {
      await twoFactorAdmin.deleteInstancePolicy(existing.id)
    } else {
      await twoFactorAdmin.upsertInstancePolicy(userType, true)
    }
    policies.value = await twoFactorAdmin.listInstancePolicies()
  } catch (e: any) {
    error.value = e?.response?.data?.message || t('common.error')
  }
  saving.value = null
}

function userTypeLabel(name: string): string {
  const key = `twoFactor.admin.userTypes.${name}`
  const translated = t(key)
  return translated === key ? name : translated
}

onMounted(load)
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
