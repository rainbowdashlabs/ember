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
import {parseFieldConfig, type ProfileField} from '@/api/profileFields'
import { profileFields } from '@/api'
import { decodeProfileValues, getFieldValue, setFieldValue } from '@/util/profileFields'
import { useSession } from '@/composables/useSession'
import { useSidebarCounts } from '@/composables/useSidebarCounts'
import { useAsyncLoader } from '@/composables/useAsyncLoader'
import AccountCard from './indexview/AccountCard.vue'
import IncompleteFieldsAlert from './indexview/IncompleteFieldsAlert.vue'
import ProfileFieldsForm from './indexview/ProfileFieldsForm.vue'
import MemberDocumentsPanel from '@/components/documents/MemberDocumentsPanel.vue'
import {valueFields} from '@/components/profilefields/fieldLayout'
import {usePermissions} from '@/composables/usePermissions'
import {StationPermission} from '@/api/types'

const { t } = useI18n()
const { sessionInfo } = useSession()
const { refresh: refreshSidebarCounts } = useSidebarCounts()

const fields = ref<ProfileField[]>([])
const values = ref<Map<number, string>>(new Map())

const memberId = computed(() => sessionInfo.value?.member?.id ?? null)

/** Reading one's own documents needs nothing; adding to them is a right a station grants. */
const {hasPermission} = usePermissions()

const canUploadOwn = computed(() => hasPermission(StationPermission.MEMBER_SELF_UPLOAD))

const fullName = computed(() => {
  const account = sessionInfo.value?.account
  if (!account) return ''
  return `${account.firstName ?? ''} ${account.lastName ?? ''}`.trim()
})

const accountEmail = computed(() => sessionInfo.value?.account?.email ?? '')

/**
 * The questions this profile asks, as the server works them out.
 *
 * <p>Which questions reach whom is one rule, and it used to be written twice: once here and once on
 * the server, from where the editing screen reads it. They drifted, and the copy here threw away
 * every question a station asks of one group, so somebody in the instructors' group was never shown
 * what the instructors are asked. Asked for rather than worked out again.
 */
const editableFields = computed(() => fields.value)

const incompleteFields = computed(() => {
  return editableFields.value.filter(f => {
    const cfg = parseFieldConfig(f.config)
    if (!cfg.required || cfg.readonly) return false
    const val = getValue(f.id)
    return !val || val === '""' || val === '' || val === 'null'
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
    profileFields.getMemberFields(memberId.value),
    profileFields.getValues(memberId.value),
  ])
  fields.value = allFields
  values.value = decodeProfileValues(profileValues)
})

async function saveProfile() {
  if (!memberId.value) return
  error.value = ''
  try {
    const entries = valueFields(editableFields.value)
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

        <MemberDocumentsPanel :member-id="memberId" :can-upload="canUploadOwn"/>
      </template>
    </div>
  </ViewContent>
</template>
