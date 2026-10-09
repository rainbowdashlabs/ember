/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import FileUploadField from '@/components/input/FileUploadField.vue'
import PageHeader from '@/components/typography/PageHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import {signing} from '@/api'
import type {SealVerification} from '@/api/generated/schema'
import {describeFailure, FailureKind, type Failure} from '@/util/failure'
import SealVerificationExplainer from './sealverificationview/SealVerificationExplainer.vue'
import SealVerificationResult from './sealverificationview/SealVerificationResult.vue'

/**
 * The public page where anybody checks a PDF against the seals of this installation's stations,
 * without signing in. The file goes to the server for the check and nothing of it is kept; the
 * answer is drawn here and gone with the page.
 */
const {t} = useI18n()

const checking = ref(false)
const result = ref<SealVerification | null>(null)
const checkedName = ref('')
const failure = ref<Failure | null>(null)

async function check(file: File) {
  checking.value = true
  result.value = null
  failure.value = null
  try {
    result.value = await signing.verifySeals(file)
    checkedName.value = file.name
  } catch (e) {
    failure.value = describeRefusal(e)
  } finally {
    checking.value = false
  }
}

/**
 * Why the file was not checked. A refusal of the file itself (none sent, too large, not a PDF, cut
 * off on the way) is the reader's to put right with another file, so it gets this page's own
 * guidance rather than the general one, which speaks of a station's storage.
 */
function describeRefusal(e: unknown): Failure {
  const described = describeFailure(e, t)
  const aboutTheFile = described.kind === FailureKind.REJECTED || described.kind === FailureKind.TOO_LARGE
  return aboutTheFile
    ? {...described, guidance: t('sealVerification.refusalGuidance'), reportable: false}
    : described
}
</script>

<template>
  <div class="max-w-3xl mx-auto px-4 py-8 space-y-6">
    <div class="space-y-2">
      <PageHeader>{{ t('sealVerification.title') }}</PageHeader>
      <MutedText tag="p" size="sm">{{ t('sealVerification.intro') }}</MutedText>
      <MutedText tag="p" size="sm">{{ t('sealVerification.nothingStored') }}</MutedText>
    </div>

    <FileUploadField
        :max-size="signing.SEAL_CHECK_MAX_BYTES"
        :disabled="checking"
        :label="t('sealVerification.choose')"
        accept="application/pdf,.pdf"
        droppable
        @select="check"
    />

    <Spinner v-if="checking" size="lg"/>
    <FailureAlert :failure="failure"/>
    <SealVerificationResult v-if="result" :result="result" :file-name="checkedName"/>

    <SealVerificationExplainer/>
  </div>
</template>
