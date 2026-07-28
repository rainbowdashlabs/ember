/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import type { ProfileField } from '@/api/profileFields'
import { parseFieldConfig } from '@/api/profileFields'
import { StationUserType } from '@/api/types'
import { profileFields } from '@/api'
import { decodeProfileValues, getFieldValue, setFieldValue } from '@/util/profileFields'
import { useSession } from '@/composables/useSession'
import { useSidebarCounts } from '@/composables/useSidebarCounts'
import { useAsyncLoader } from '@/composables/useAsyncLoader'
import AccountCard from './indexview/AccountCard.vue'
import IncompleteFieldsAlert from './indexview/IncompleteFieldsAlert.vue'
import ProfileFieldsForm from './indexview/ProfileFieldsForm.vue'

function getUserScopes(userType?: string): string[] {
  const scopes: string[] = []
  if (!userType) return scopes
  if (userType === StationUserType.MEMBER || userType === StationUserType.TRIAL) {
    scopes.push(StationUserType.MEMBER)
  }
  if (userType === StationUserType.TEAM || userType === StationUserType.MANAGER) {
    scopes.push(StationUserType.TEAM)
  }
  if (userType === StationUserType.GUARDIAN) {
    scopes.push(StationUserType.GUARDIAN)
  }
  return scopes
}

const { t } = useI18n()
const { sessionInfo } = useSession()
const { refresh: refreshSidebarCounts } = useSidebarCounts()

const fields = ref<ProfileField[]>([])
const values = ref<Map<number, string>>(new Map())

const memberId = computed(() => sessionInfo.value?.member?.id ?? null)

const userScopes = computed(() => getUserScopes(sessionInfo.value?.userType))

const fullName = computed(() => {
  const account = sessionInfo.value?.account
  if (!account) return ''
  return `${account.firstName ?? ''} ${account.lastName ?? ''}`.trim()
})

const accountEmail = computed(() => sessionInfo.value?.account?.email ?? '')

const editableFields = computed(() => {
  return fields.value.filter(f => {
    if (f.scope === 'GROUP') return false
    return userScopes.value.includes(f.scope ?? StationUserType.MEMBER)
  })
})

const incompleteFields = computed(() => {
  return editableFields.value.filter(f => {
    const cfg = parseFieldConfig(f.config)
    if (!cfg.required || cfg.readonly) return false
    const val = getValue(f.id)
    return !val || val === '""' || val === ''
  })
})

function getValue(fieldId: number): string {
  return getFieldValue(values, fieldId)
}

function setValue(fieldId: number, val: string) {
  setFieldValue(values, fieldId, val)
}

const { loading, error, reload } = useAsyncLoader(async () => {
  if (!memberId.value) return
  const [allFields, profileValues] = await Promise.all([
    profileFields.listFields(),
    profileFields.getValues(memberId.value),
  ])
  fields.value = allFields
  values.value = decodeProfileValues(profileValues)
})

async function saveProfile() {
  if (!memberId.value) return
  error.value = ''
  try {
    const entries = editableFields.value
      .filter(f => !parseFieldConfig(f.config).readonly)
      .map(f => ({ fieldId: f.id, value: JSON.stringify(getValue(f.id)) }))
    await profileFields.setValues(memberId.value, { values: entries })
    refreshSidebarCounts()
  } catch (e) {
    error.value = t('common.error')
    throw e
  }
}

watch(memberId, (newId) => {
  if (newId) reload()
})
</script>

<template>
  <ViewContent
      :title="t('pages.profile.title')"
      :subtitle="t('pages.profile.subtitle')"
  >
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg" />
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading && memberId">
        <AccountCard
            :account-uid="sessionInfo?.account?.uid"
            :full-name="fullName"
            :account-email="accountEmail"
        />

        <IncompleteFieldsAlert :incomplete-fields="incompleteFields" />

        <ProfileFieldsForm
            :editable-fields="editableFields"
            :get-value="getValue"
            :save-action="saveProfile"
            @update="setValue"
        />
      </template>
    </div>
  </ViewContent>
</template>
