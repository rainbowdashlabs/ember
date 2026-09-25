/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, ref} from 'vue'
import {useRouter} from 'vue-router'
import {useI18n} from 'vue-i18n'
import {session} from '@/api'
import {acceptStorage} from '@/api/storage'
import {useConsentGuard} from '@/composables/useConsentGuard'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {describeFailure, type Failure} from '@/util/failure'
import type {ConsentChangesResponse} from '@/api/session'
import Spinner from '@/components/feedback/Spinner.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import InfoContainer from '@/components/container/InfoContainer.vue'
import SuccessButton from '@/components/button/SuccessButton.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import PolicyChangeSection from '@/views/reconsentview/PolicyChangeSection.vue'

const {t} = useI18n()
const router = useRouter()

const loading = ref(true)
const loadFailure = ref<Failure | null>(null)
const changes = ref<ConsentChangesResponse | null>(null)

onMounted(async () => {
  try {
    const status = await session.getConsentStatus()
    if (status.current) {
      await router.replace({name: 'dashboard-overview'})
      return
    }
    changes.value = await session.getConsentChanges()
  } catch (e) {
    loadFailure.value = describeFailure(e, t)
  }
  loading.value = false
})

const {running: submitting, failure: submitFailure, run: handleAccept} = useAsyncAction(async () => {
  if (!changes.value) return
  await session.recordConsent({
    consentVersion: changes.value.currentConsentVersion,
    privacyVersion: changes.value.currentPrivacyVersion,
    tosVersion: changes.value.currentTosVersion,
  })
  acceptStorage({
    consent: changes.value.currentConsentVersion,
    privacy: changes.value.currentPrivacyVersion,
    tos: changes.value.currentTosVersion,
  })
  const {setNeedsReconsent} = useConsentGuard()
  setNeedsReconsent(false)
  await router.replace({name: 'dashboard-overview'})
})

/**
 * The one alert this page has, fed by whichever of the two failed.
 *
 * <p>Reading the consent changes and accepting them are separate acts, so whichever went wrong is
 * the one described: they never overlap, because nothing can be accepted until it has been read.
 */
const failure = computed(() => loadFailure.value ?? submitFailure.value)
</script>

<template>
  <div class="flex justify-center px-4 py-12">
    <div class="w-full max-w-3xl space-y-6">
      <SectionHeader>{{ t('reconsent.title') }}</SectionHeader>
      <p class="text-sm text-(--text-muted)">{{ t('reconsent.description') }}</p>

      <Spinner v-if="loading" size="lg"/>
      <FailureAlert :failure="failure"/>

      <template v-if="changes && !loading">
        <PolicyChangeSection v-if="changes.privacyChanged"
                             :title="t('reconsent.privacyChanged')"
                             :diff="changes.privacyDiff"
                             :html="changes.privacyHtml"
                             added-key-prefix="pa-"
                             removed-key-prefix="pr-"/>

        <PolicyChangeSection v-if="changes.tosChanged"
                             :title="t('reconsent.tosChanged')"
                             :diff="changes.tosDiff"
                             :html="changes.tosHtml"
                             added-key-prefix="ta-"
                             removed-key-prefix="tr-"/>

        <InfoContainer class="space-y-2">
          <p class="text-sm">{{ t('reconsent.consentNote') }}</p>
        </InfoContainer>

        <SuccessButton class="w-full" :disabled="submitting" @click="handleAccept">
          {{ submitting ? t('common.loading') : t('reconsent.accept') }}
        </SuccessButton>
      </template>
    </div>
  </div>
</template>
