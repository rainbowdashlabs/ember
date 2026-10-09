/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import SavedSignatureSection from './accountsignatureview/SavedSignatureSection.vue'
import NewSignatureSection from './accountsignatureview/NewSignatureSection.vue'
import SignatureConsentSection from './accountsignatureview/SignatureConsentSection.vue'
import {useOwnSignature} from '@/composables/useOwnSignature'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import type {SignatureImageSource} from '@/api/generated/schema'
import {describeFailure, type Failure} from '@/util/failure'

/**
 * The reader's own signature in the account settings: the picture saved for signing, a new one drawn,
 * typed or uploaded, and the consent to letters they issue being signed with it automatically.
 *
 * <p>The picture belongs to the account and is the same at every station. It goes into a document only
 * when its owner signs, or under the consent given here.
 */
const {t} = useI18n()
const {settings, imageUrl, load, save, remove, consent} = useOwnSignature()

const busy = ref(false)
const failure = ref<Failure | null>(null)

const {loading, failure: loadFailure} = useAsyncLoader(load)

/** Runs one change, one at a time, keeping its failure for the reader. */
async function change(action: () => Promise<void>) {
  if (busy.value) return
  busy.value = true
  failure.value = null
  try {
    await action()
  } catch (e) {
    failure.value = describeFailure(e, t)
  } finally {
    busy.value = false
  }
}

function saveImage(image: Blob, source: SignatureImageSource) {
  void change(() => save(image, source))
}
</script>

<template>
  <ViewContent :title="t('pages.account-signature.title')" :subtitle="t('pages.account-signature.subtitle')">
    <div class="space-y-6 max-w-3xl">
      <Spinner v-if="loading"/>
      <FailureAlert :failure="loadFailure ?? failure"/>
      <template v-if="!loading && settings">
        <SavedSignatureSection :settings="settings" :image-url="imageUrl" :busy="busy" @remove="change(remove)"/>
        <NewSignatureSection :busy="busy" @save="saveImage"/>
        <SignatureConsentSection :settings="settings" :busy="busy" @change="value => change(() => consent(value))"/>
      </template>
    </div>
  </ViewContent>
</template>
