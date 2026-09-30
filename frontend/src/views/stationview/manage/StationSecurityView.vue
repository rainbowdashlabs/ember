/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import ConfirmDeleteModal from '@/components/feedback/ConfirmDeleteModal.vue'
import PoliciesPanel from './stationsecurityview/PoliciesPanel.vue'
import MembersPanel from './stationsecurityview/MembersPanel.vue'
import {twoFactorAdmin} from '@/api'
import type {MemberStatus, TwoFactorPolicy} from '@/api/twoFactorAdmin'
import {StationPermission} from '@/api/types'
import {useSession} from '@/composables/useSession'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {describeFailure} from '@/util/failure'

const {t} = useI18n()
const {hasPermission, loaded} = useSession()
const router = useRouter()

watch(loaded, (isLoaded) => {
  if (isLoaded && !hasPermission(StationPermission.STATION_ADMINISTRATOR)) {
    router.replace('/station/dashboard/overview')
  }
}, {immediate: true})

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

const {loading, failure, reload} = useAsyncLoader(async () => {
  const [types, p, m] = await Promise.all([
    twoFactorAdmin.listAssignableUserTypes(),
    twoFactorAdmin.listStationPolicies(),
    twoFactorAdmin.listStationMemberStatus(),
  ])
  userTypes.value = types
  policies.value = p
  members.value = m
})

/** Reads the page back, naming a failure with the sentence given where the caller has a better one. */
async function load(staleMessage?: string) {
  await reload()
  if (staleMessage && failure.value) failure.value = {...failure.value, message: staleMessage}
}

/**
 * Turns the requirement on or off for one kind of member, then reads the page back.
 *
 * <p>The read is answered for separately: a policy that was written and a page that then failed to
 * refresh used to report a refused write, and the reader flicks the switch again.
 */
async function togglePolicy(userType: string) {
  const existing = policyByUserType.value.get(userType)
  saving.value = userType
  try {
    if (existing && existing.required) {
      await twoFactorAdmin.deleteStationPolicy(existing.id)
    } else {
      await twoFactorAdmin.upsertStationPolicy(userType, true)
    }
  } catch (e) {
    failure.value = describeFailure(e, t)
    saving.value = null
    return
  }
  saving.value = null
  await load(t('failure.staleAfterAction'))
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
  } catch (e) {
    failure.value = describeFailure(e, t)
    resetLoading.value = false
    return
  }
  resetLoading.value = false
  await load(t('failure.staleAfterAction'))
}
</script>

<template>
  <ViewContent
      :title="t('pages.station-security.title')"
      :subtitle="t('pages.station-security.subtitle')"
  >
    <div class="space-y-6">
      <Spinner v-if="loading" size="md"/>
      <FailureAlert :failure="failure"/>

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
            @reset="openReset"
        />
      </template>

      <ConfirmDeleteModal
          :model-value="resetTarget !== null"
          :title="t('twoFactor.admin.resetConfirmTitle')"
          :message="t('twoFactor.admin.resetConfirmText', {name: `${resetTarget?.firstName} ${resetTarget?.lastName}`})"
          :confirm-label="t('twoFactor.admin.reset')"
          :busy="resetLoading"
          @update:model-value="resetTarget = null"
          @confirm="confirmReset"
      >
        <Alert variant="error">{{ t('twoFactor.admin.resetWarning') }}</Alert>
      </ConfirmDeleteModal>
    </div>
  </ViewContent>
</template>
