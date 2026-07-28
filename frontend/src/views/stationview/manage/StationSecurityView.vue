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
import SubHeader from '@/components/typography/SubHeader.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Modal from '@/components/feedback/Modal.vue'
import PoliciesPanel from './stationsecurityview/PoliciesPanel.vue'
import MembersPanel from './stationsecurityview/MembersPanel.vue'
import {twoFactorAdmin} from '@/api'
import type {MemberStatus, TwoFactorPolicy} from '@/api/twoFactorAdmin'
import {StationPermission} from '@/api/types'
import {useSession} from '@/composables/useSession'
import {apiErrorMessage} from '@/util/apiError'

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
  } catch (e) {
    error.value = apiErrorMessage(e) || t('common.error')
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
  } catch (e) {
    error.value = apiErrorMessage(e) || t('common.error')
  }
  saving.value = null
}

function userTypeLabel(name: string): string {
  const key = `twoFactor.admin.userTypes.${name}`
  const translated = t(key)
  return translated === key ? name : translated
}

const resetTarget = ref<MemberStatus | null>(null)
const resetLoading = ref(false)

function openReset(member: MemberStatus) {
  resetTarget.value = member
}

async function confirmReset() {
  if (!resetTarget.value) return
  resetLoading.value = true
  try {
    await twoFactorAdmin.resetAccount2FAByStationAdmin(resetTarget.value.accountId)
    resetTarget.value = null
    await load()
  } catch (e) {
    error.value = apiErrorMessage(e) || t('common.error')
  }
  resetLoading.value = false
}

onMounted(load)
</script>

<template>
  <ViewContent
      :title="t('pages.station-security.title')"
      :subtitle="t('pages.station-security.subtitle')"
  >
    <div class="space-y-6">
      <Spinner v-if="loading" size="md"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading">
        <PoliciesPanel
            :user-types="userTypes"
            :policy-by-user-type="policyByUserType"
            :saving="saving"
            :user-type-label="userTypeLabel"
            @toggle="togglePolicy"
        />
        <MembersPanel
            :members="members"
            :user-type-label="userTypeLabel"
            @reset="openReset"
        />
      </template>

      <Modal :model-value="resetTarget !== null" size="sm" @update:model-value="resetTarget = null">
        <div v-if="resetTarget" class="space-y-4 p-4">
          <SubHeader>{{ t('twoFactor.admin.resetConfirmTitle') }}</SubHeader>
          <p class="text-sm">
            {{ t('twoFactor.admin.resetConfirmText', {name: resetTarget.firstName + ' ' + resetTarget.lastName}) }}
          </p>
          <Alert variant="error">{{ t('twoFactor.admin.resetWarning') }}</Alert>
          <div class="flex justify-end gap-2">
            <SecondaryButton :disabled="resetLoading" @click="resetTarget = null">
              {{ t('common.cancel') }}
            </SecondaryButton>
            <ErrorButton :disabled="resetLoading" @click="confirmReset">
              {{ resetLoading ? t('common.loading') : t('twoFactor.admin.reset') }}
            </ErrorButton>
          </div>
        </div>
      </Modal>
    </div>
  </ViewContent>
</template>
