/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, onMounted} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import SuccessContainer from '@/components/container/SuccessContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import PublicFormBody from './publicformsubmitview/PublicFormBody.vue'
import {usePublicFormSubmission} from '@/composables/usePublicFormSubmission'

const {t} = useI18n()
const route = useRoute()

const stationUid = computed(() => String(route.params.stationUid))
const publicUid = computed(() => String(route.params.publicUid))

const {
  form,
  answers,
  loading,
  submitted,
  consentAccepted,
  consentVersion,
  privacyVersion,
  tosVersion,
  submitting,
  error,
  load,
  toggleChoice,
  updateText,
  updateDate,
  submit,
} = usePublicFormSubmission(stationUid, publicUid)

onMounted(load)
</script>

<template>
  <ViewContent :title="t('pages.public-form-submit.title')" :subtitle="t('pages.public-form-submit.subtitle')">
    <div class="space-y-6 max-w-3xl">
      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <SuccessContainer v-if="submitted">
        <SectionHeader>{{ t('publicForm.thanksTitle') }}</SectionHeader>
        <p class="mt-2 text-sm">{{ t('publicForm.thanksText') }}</p>
      </SuccessContainer>

      <PublicFormBody
          v-if="!loading && form && !submitted"
          :form="form"
          :answers="answers"
          v-model:consent-accepted="consentAccepted"
          v-model:consent-version="consentVersion"
          v-model:privacy-version="privacyVersion"
          v-model:tos-version="tosVersion"
          :submitting="submitting"
          @update-text="updateText"
          @update-date="updateDate"
          @toggle-choice="toggleChoice"
          @submit="submit"/>
    </div>
  </ViewContent>
</template>
