/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute} from 'vue-router'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import SuccessContainer from '@/components/container/SuccessContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import PublicFormBody from './publicformsubmitview/PublicFormBody.vue'
import SharedLinkShell from './SharedLinkShell.vue'
import {usePublicFormSubmission} from '@/composables/usePublicFormSubmission'
import {usePublicFailure} from '@/composables/usePublicFailure'
import {apiUrl} from '@/util/apiUrl'
import {socialMeta, stationLogoImage, useAbsoluteUrl} from '@/util/socialMeta'
import type {PublicForm} from '@/api/publicForms'
import type {SharedBrand} from '@/api/sharedLinks'

/**
 * A form somebody was sent the link to.
 *
 * <p>Fetched while the server renders and handed to the composable that holds the answers, so the
 * questions are in the page the server sends rather than appearing once scripts have run. A link
 * whose whole purpose is to be pasted into a message has to carry what it opens.
 */
const {t} = useI18n()
const route = useRoute()

const token = computed(() => String(route.params.token))
const stationUid = ref<string | null>(null)
const publicUid = ref<string | null>(null)

/**
 * The form and the station behind the link, fetched while the server renders.
 *
 * <p>Once, not once per side: the form travels in the page the server sends, so a link pasted into a
 * message draws a real preview and a reader with no scripts still sees the questions. Fetching again
 * in the browser would cost a second request and leave the page blank until it came back.
 */
const {data: preview, error: previewError} = await useAsyncData(
    () => `shared-form-${token.value}`,
    async () => {
      const [form, brand] = await Promise.all([
        $fetch<PublicForm>(`${apiUrl('')}/public/shared-form/${token.value}`),
        $fetch<SharedBrand>(`${apiUrl('')}/public/shared-form/${token.value}/brand`).catch(() => null),
      ])
      return {form, brand}
    },
    {watch: [token]},
)

const preloadedForm = computed(() => preview.value?.form ?? null)

const absoluteUrl = useAbsoluteUrl()

const {
  form,
  open,
  answers,
  submitted,
  consentAccepted,
  consentVersion,
  privacyVersion,
  tosVersion,
  submitting,
  failure,
  validationError,
  toggleChoice,
  updateText,
  updateDate,
  submit,
} = usePublicFormSubmission(stationUid, publicUid, token, preloadedForm)

const linkFailure = usePublicFailure(previewError, {
  message: 'shareLink.gone',
  guidance: 'shareLink.goneGuidance',
})

useHead(computed(() => {
  const f = preview.value?.form
  const title = f?.title ?? t('pages.public-form-submit.title')
  const description = f?.description || title
  return {
    title,
    meta: [
      {name: 'robots', content: 'noindex, nofollow'},
      {name: 'referrer', content: 'no-referrer'},
      ...socialMeta({title, description, imageUrl: absoluteUrl(stationLogoImage(preview.value?.brand))}),
    ],
  }
}))
</script>

<template>
    <SharedLinkShell :brand="preview?.brand ?? null">
        <div class="space-y-6">
            <FailureAlert :failure="linkFailure ?? failure"/>
            <FailureAlert :message="validationError" expected/>

            <SuccessContainer v-if="submitted">
                <SectionHeader>{{ t('publicForm.thanksTitle') }}</SectionHeader>
                <p class="mt-2 text-sm">{{ t('publicForm.thanksText') }}</p>
            </SuccessContainer>

            <PublicFormBody
                v-if="form && !submitted"
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
    </SharedLinkShell>
</template>
