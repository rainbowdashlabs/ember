/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref, onMounted} from 'vue'
import {useI18n} from 'vue-i18n'
import Alert from '@/components/feedback/Alert.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import {stationApplications, adminSettings} from '@/api'
import PageHeader from '@/components/typography/PageHeader.vue'
import PageHeroIcon from '@/components/typography/PageHeroIcon.vue'
import ApplyForm from '@/views/applyview/ApplyForm.vue'
import BackToLoginLink from '@/views/applyview/BackToLoginLink.vue'

const {t} = useI18n()

const firstName = ref('')
const lastName = ref('')
const email = ref('')
const stationName = ref('')
const introduction = ref('')
const submitting = ref(false)
const error = ref('')
const submitted = ref(false)
const registrationEnabled = ref(true)
const loading = ref(true)

onMounted(async () => {
  try {
    registrationEnabled.value = await adminSettings.isRegistrationEnabled()
  } catch {
    registrationEnabled.value = true
  } finally {
    loading.value = false
  }
})

async function submit() {
  if (!firstName.value.trim() || !lastName.value.trim() || !email.value.trim() || !stationName.value.trim()) {
    error.value = t('apply.allFieldsRequired')
    return
  }
  submitting.value = true
  error.value = ''
  try {
    await stationApplications.submit({
      firstName: firstName.value.trim(),
      lastName: lastName.value.trim(),
      email: email.value.trim(),
      stationName: stationName.value.trim(),
      introduction: introduction.value.trim(),
    })
    submitted.value = true
  } catch {
    error.value = t('common.error')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="flex items-center justify-center px-4 py-12">
    <div class="w-full max-w-md space-y-6">
      <div class="text-center">
        <PageHeroIcon :icon="['fas', 'building']"/>
        <PageHeader class="text-2xl font-bold">{{ t('apply.title') }}</PageHeader>
      </div>

      <Spinner v-if="loading" size="md" />

      <template v-else-if="!registrationEnabled">
        <Alert variant="info">{{ t('apply.disabled') }}</Alert>
        <BackToLoginLink/>
      </template>

      <template v-else-if="!submitted">
        <p class="text-sm text-(--text-muted)">{{ t('apply.hint') }}</p>
        <Alert v-if="error" variant="error">{{ error }}</Alert>
        <ApplyForm v-model:first-name="firstName" v-model:last-name="lastName" v-model:email="email"
                   v-model:station-name="stationName" v-model:introduction="introduction"
                   :submitting="submitting" @submit="submit"/>
        <BackToLoginLink/>
      </template>

      <template v-else>
        <Alert variant="success">{{ t('apply.success') }}</Alert>
        <BackToLoginLink/>
      </template>
    </div>
  </div>
</template>
