/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {onUnmounted, ref, useId} from 'vue'
import {useI18n} from 'vue-i18n'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import AppLink from '@/components/navigation/AppLink.vue'
import DocumentPdfFrame from '@/components/documents/DocumentPdfFrame.vue'
import {getSigningDocument} from '@/api/signing'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {saveBlob} from '@/util/saveBlob'

/**
 * The document to sign, exactly as the request froze it.
 *
 * <p>The page is drawn on a canvas, which a screen reader cannot read, so the same bytes are offered
 * twice more: saved as a file, and opened in the browser's own viewer in a new tab, where a screen
 * reader and the browser's zoom reach the text. The hint saying so comes before the drawing, so a
 * reader who cannot see it hears where to go before meeting it.
 *
 * <p>A document that could not be opened says so in place of the drawing.
 */
const props = defineProps<{
  fieldId: number
  title: string
  fileName: string
}>()

const {t} = useI18n()
const headingId = useId()
const hintId = useId()

const pdf = ref<Blob | null>(null)
const address = ref<string | null>(null)

const {loading, failure} = useAsyncLoader(async (isCurrent) => {
  const loaded = await getSigningDocument(props.fieldId)
  if (!isCurrent()) return
  pdf.value = loaded
  address.value = URL.createObjectURL(new Blob([loaded], {type: 'application/pdf'}))
})

function save() {
  if (pdf.value) void saveBlob(pdf.value, props.fileName)
}

onUnmounted(() => {
  if (address.value) URL.revokeObjectURL(address.value)
})
</script>

<template>
  <section :aria-labelledby="headingId" class="space-y-3">
    <SubHeader :id="headingId">{{ t('signing.document.heading') }}</SubHeader>
    <Spinner v-if="loading" size="md"/>
    <FailureAlert v-else-if="failure" :failure="failure"/>
    <template v-else-if="pdf">
      <MutedText :id="hintId" tag="p" size="sm">{{ t('signing.document.accessibleHint') }}</MutedText>
      <ButtonRow>
        <SecondaryButton type="button" :icon="['fas', 'download']" data-testid="signing-document-save" @click="save">
          {{ t('signing.document.save') }}
        </SecondaryButton>
        <AppLink v-if="address" :href="address" :icon="['fas', 'file-pdf']" external class="self-center">
          {{ t('signing.document.openInViewer') }}
        </AppLink>
      </ButtonRow>
      <div role="group" :aria-label="t('signing.document.drawnLabel', {title})" :aria-describedby="hintId">
        <DocumentPdfFrame :pdf="pdf" :title="title"/>
      </div>
    </template>
  </section>
</template>
