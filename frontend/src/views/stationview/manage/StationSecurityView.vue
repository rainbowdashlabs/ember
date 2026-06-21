/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import TableHeaderCell from '@/components/typography/TableHeaderCell.vue'
import {twoFactorAdmin} from '@/api'
import type {MemberStatus, TwoFactorPolicy} from '@/api/twoFactorAdmin'
import {StationPermission} from '@/api/types'
import {useSession} from '@/composables/useSession'

const {t} = useI18n()
const {hasPermission, loaded} = useSession()
const router = useRouter()

watch(loaded, (isLoaded) => {
  if (isLoaded && !hasPermission(StationPermission.STATION_ADMINISTRATOR)) {
    router.replace('/station/dashboard/overview')
  }
}, {immediate: true})

const loading = ref(true)
const error = ref('')
const userTypes = ref<string[]>([])
const policies = ref<TwoFactorPolicy[]>([])
const members = ref<MemberStatus[]>([])
const saving = ref<string | null>(null)

const policyByUserType = computed(() => {
  const map = new Map<string, TwoFactorPolicy>()
  for (const p of policies.value) {
    if (p.userType) map.set(p.userType, p)
  }
  return map
})

const enrolledCount = computed(() => members.value.filter(m => m.enrolled).length)
const mandatedCount = computed(() => members.value.filter(m => m.mandated).length)
const mandatedNotEnrolled = computed(() => members.value.filter(m => m.mandated && !m.enrolled).length)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [t, p, m] = await Promise.all([
      twoFactorAdmin.listAssignableUserTypes(),
      twoFactorAdmin.listStationPolicies(),
      twoFactorAdmin.listStationMemberStatus(),
    ])
    userTypes.value = t
    policies.value = p
    members.value = m
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
      await twoFactorAdmin.deleteStationPolicy(existing.id)
    } else {
      await twoFactorAdmin.upsertStationPolicy(userType, true)
    }
    await load()
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
  <ViewContent>
    <div class="space-y-6">
      <div>
        <SectionHeader>{{ t('twoFactor.admin.title') }}</SectionHeader>
        <MutedText tag="p" size="sm">{{ t('twoFactor.admin.subtitle') }}</MutedText>
      </div>

      <Spinner v-if="loading" size="md"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading">
        <NeutralContainer class="space-y-3">
          <SubHeader>{{ t('twoFactor.admin.policiesTitle') }}</SubHeader>
          <MutedText tag="p" size="sm">{{ t('twoFactor.admin.policiesHint') }}</MutedText>
          <ul class="space-y-2">
            <li v-for="ut in userTypes" :key="ut"
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

        <NeutralContainer class="space-y-3">
          <SubHeader>{{ t('twoFactor.admin.membersTitle') }}</SubHeader>
          <div class="flex flex-wrap gap-2 text-sm">
            <SuccessBadge>{{ t('twoFactor.admin.enrolledCount', {n: enrolledCount, total: members.length}) }}</SuccessBadge>
            <InfoBadge>{{ t('twoFactor.admin.mandatedCount', {n: mandatedCount}) }}</InfoBadge>
            <ErrorBadge v-if="mandatedNotEnrolled > 0">
              {{ t('twoFactor.admin.gapCount', {n: mandatedNotEnrolled}) }}
            </ErrorBadge>
          </div>
          <div class="overflow-x-auto">
            <table class="w-full text-sm">
              <thead>
                <tr class="text-left text-(--text-muted)">
                  <TableHeaderCell>{{ t('twoFactor.admin.col.name') }}</TableHeaderCell>
                  <TableHeaderCell>{{ t('twoFactor.admin.col.email') }}</TableHeaderCell>
                  <TableHeaderCell>{{ t('twoFactor.admin.col.userType') }}</TableHeaderCell>
                  <TableHeaderCell>{{ t('twoFactor.admin.col.status') }}</TableHeaderCell>
                </tr>
              </thead>
              <tbody>
                <tr v-for="m in members" :key="m.memberId" class="border-t border-(--border)">
                  <td class="py-2 pr-3">{{ m.firstName }} {{ m.lastName }}</td>
                  <td class="py-2 pr-3 text-(--text-muted)">{{ m.email }}</td>
                  <td class="py-2 pr-3">{{ userTypeLabel(m.userType) }}</td>
                  <td class="py-2 pr-3">
                    <SuccessBadge v-if="m.enrolled">{{ t('twoFactor.admin.statusEnrolled') }}</SuccessBadge>
                    <ErrorBadge v-else-if="m.mandated">{{ t('twoFactor.admin.statusMandatedGap') }}</ErrorBadge>
                    <InfoBadge v-else>{{ t('twoFactor.admin.statusOptional') }}</InfoBadge>
                  </td>
                </tr>
                <tr v-if="members.length === 0">
                  <td colspan="4" class="py-4 text-(--text-muted) text-center">{{ t('twoFactor.admin.noMembers') }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </NeutralContainer>
      </template>
    </div>
  </ViewContent>
</template>
