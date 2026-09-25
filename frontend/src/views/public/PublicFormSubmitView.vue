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
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import SuccessContainer from '@/components/container/SuccessContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import PublicFormBody from './publicformsubmitview/PublicFormBody.vue'
import {usePublicFormSubmission} from '@/composables/usePublicFormSubmission'
import type {PublicForm} from '@/api/publicForms'
import {apiUrl} from '@/util/apiUrl'
import {socialMeta, stationLogoImage, titleWithStation, useAbsoluteUrl} from '@/util/socialMeta'
import {usePublicStationAddress} from '@/composables/usePublicStationAddress'

const {t} = useI18n()
const route = useRoute()
const {station} = usePublicStationAddress()

/**
 * A form is reached either by the link it was sent with, which names no station, or by station and
 * form where it is linked from a public page. Both end in this view.
 */
const shareToken = computed(() => (route.params.token ? String(route.params.token) : null))
const stationUid = computed(() => (route.params.stationUid ? String(route.params.stationUid) : null))
const publicUid = computed(() => (route.params.publicUid ? String(route.params.publicUid) : null))

const absoluteUrl = useAbsoluteUrl()

/**
 * Resolved here rather than inside the loader: the address comes from the runtime configuration,
 * and reading that needs the Nuxt instance, which a loader running on its own no longer has.
 */
const apiBase = apiUrl('')

/**
 * The form as the station's page links to it, fetched while the server renders.
 *
 * <p>A form's own title and its own description are what a link to it unfurls as, and fetched after
 * mounting they were in no page anything outside the browser ever read. A form reached by the link
 * it was sent with names no station, so there is nothing to ask for here and the loader below is
 * what fetches it.
 */
const {data: preloadedForm} = await useAsyncData(
    () => `public-form-${stationUid.value}-${publicUid.value}`,
    () => (stationUid.value && publicUid.value
        ? $fetch<PublicForm>(`${apiBase}/public/${stationUid.value}/forms/${publicUid.value}`)
        : Promise.resolve(null)),
    {watch: [stationUid, publicUid]},
)

const {
  form,
  open,
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
} = usePublicFormSubmission(stationUid, publicUid, shareToken, computed(() => preloadedForm.value ?? null))

useHead(computed(() => {
  const info = station.value
  const title = form.value?.title || titleWithStation(t('pages.public-form-submit.title'), info?.name)
  const description = form.value?.description
      || (info ? t('publicStation.meta.form', {station: info.name}) : title)
  return {
    title,
    meta: socialMeta({title, description, imageUrl: absoluteUrl(stationLogoImage(info))}),
  }
}))

onMounted(load)
</script>

<template>
  <ViewContent :title="t('pages.public-form-submit.title')" :subtitle="t('pages.public-form-submit.subtitle')">
    <div class="space-y-6 max-w-3xl">
      <Spinner v-if="loading" size="lg"/>
      <FailureAlert :message="error"/>

      <SuccessContainer v-if="submitted">
        <SectionHeader>{{ t('publicForm.thanksTitle') }}</SectionHeader>
        <p class="mt-2 text-sm">{{ t('publicForm.thanksText') }}</p>
      </SuccessContainer>

      <PublicFormBody
          v-if="!loading && form && !submitted"
          :form="form"
          :open="open"
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
