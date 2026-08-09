/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import Alert from '@/components/feedback/Alert.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import PageHeader from '@/components/typography/PageHeader.vue'
import PageHeroIcon from '@/components/typography/PageHeroIcon.vue'
import InviteHeader from './waitinglistregisterview/InviteHeader.vue'
import RegisterForm from './waitinglistregisterview/RegisterForm.vue'
import RegisterSuccess from './waitinglistregisterview/RegisterSuccess.vue'
import type { GuardianInput, WaitingListInviteInfo } from '@/api/waitingList'
import { waitingList } from '@/api'
import { useAsyncAction } from '@/composables/useAsyncAction'
import { getFieldValue as readFieldValue, setFieldValue as writeFieldValue } from '@/util/profileFields'
import { useLinkAccessedResource } from '@/composables/useLinkAccessedResource'

const { t } = useI18n()

const {
  credential: code,
  data: inviteInfo,
  loading,
  error: pageError,
  load: loadInviteInfo,
} = useLinkAccessedResource<WaitingListInviteInfo>(
    'code',
    () => t('waitingList.register.noCode'),
    () => t('waitingList.register.invalidCode'),
    (c) => waitingList.getInviteInfo(c),
)

const submitted = ref(false)
const accessToken = ref('')

const firstname = ref('')
const lastname = ref('')
const guardians = ref<GuardianInput[]>([{ firstname: '', lastname: '', email: '', phone: '' }])
const notes = ref('')
const fieldValues = ref<Map<number, string>>(new Map())
const consentAccepted = ref(false)
const consentVersion = ref('')
const privacyVersion = ref('')
const tosVersion = ref('')

function getFieldValue(fieldId: number): string {
  return readFieldValue(fieldValues, fieldId)
}

function setFieldValue(fieldId: number, value: string) {
  writeFieldValue(fieldValues, fieldId, value)
}

function addGuardian() {
  guardians.value = [...guardians.value, { firstname: '', lastname: '', email: '', phone: '' }]
}

function removeGuardian(index: number) {
  guardians.value = guardians.value.filter((_, i) => i !== index)
}

const { running: submitting, error: submitError, run: runSubmit } = useAsyncAction(async () => {
  const values: Record<number, string> = {}
  for (const [fieldId, value] of fieldValues.value) {
    if (value.trim()) {
      values[fieldId] = value.trim()
    }
  }
  const result = await waitingList.register({
    inviteCode: code.value,
    firstname: firstname.value.trim(),
    lastname: lastname.value.trim(),
    guardians: guardians.value.map(g => ({ firstname: g.firstname.trim(), lastname: g.lastname.trim(), email: g.email.trim(), phone: g.phone.trim() })),
    notes: notes.value.trim(),
    values,
    consentVersion: consentVersion.value,
    privacyVersion: privacyVersion.value,
    tosVersion: tosVersion.value,
  })
  accessToken.value = result.accessToken
  submitted.value = true
})

const error = computed(() => pageError.value || submitError.value)

function submit() {
  if (!firstname.value.trim() || !guardians.value.some(g => g.email.trim())) {
    pageError.value = t('waitingList.register.requiredFields')
    return
  }
  if (!consentAccepted.value) {
    pageError.value = t('publicConsent.required')
    return
  }

  if (inviteInfo.value) {
    for (const field of inviteInfo.value.fields) {
      if (field.required && !getFieldValue(field.id).trim()) {
        pageError.value = t('waitingList.register.requiredField', { name: field.name })
        return
      }
    }
  }

  pageError.value = ''
  void runSubmit()
}

const statusLink = computed(() => `${window.location.origin}/waiting-list/status?token=${accessToken.value}`)

onMounted(loadInviteInfo)
</script>

<template>
  <div class="flex items-center justify-center px-4 py-12">
    <div class="w-full max-w-lg space-y-6">
      <div class="text-center">
        <PageHeroIcon :icon="['fas', 'clipboard-list']"/>
        <PageHeader class="text-2xl font-bold">{{ t('waitingList.register.title') }}</PageHeader>
      </div>

      <Spinner v-if="loading" size="md" />
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading && inviteInfo && !submitted">
        <InviteHeader :invite-info="inviteInfo" />
        <RegisterForm
          :invite-info="inviteInfo"
          v-model:firstname="firstname"
          v-model:lastname="lastname"
          :guardians="guardians"
          v-model:notes="notes"
          v-model:consent-accepted="consentAccepted"
          v-model:consent-version="consentVersion"
          v-model:privacy-version="privacyVersion"
          v-model:tos-version="tosVersion"
          :submitting="submitting"
          :field-value-of="getFieldValue"
          @add-guardian="addGuardian"
          @remove-guardian="removeGuardian"
          @set-field-value="setFieldValue"
          @submit="submit"
        />
      </template>

      <template v-if="submitted">
        <RegisterSuccess :status-link="statusLink" />
      </template>

      <template v-if="!loading && !inviteInfo && !submitted">
        <div class="text-center">
          <router-link class="text-sm text-primary hover:underline" to="/login">{{ t('waitingList.register.backToLogin') }}</router-link>
        </div>
      </template>
    </div>
  </div>
</template>
