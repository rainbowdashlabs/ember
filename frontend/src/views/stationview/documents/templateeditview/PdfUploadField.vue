/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import FileInput from '@/components/input/FileInput.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {PdfOriginal} from '@/api/generated/schema'
import {formatDateTime} from '@/util/format'

/**
 * The PDF the template fills, and the way to upload it or a newer version of it. A new version keeps
 * every field where it was, to be checked over the new pages; documents made before keep the version
 * they were filled from. A template is saved before its first PDF goes up.
 */
defineProps<{
  pdf: PdfOriginal | null
  canUpload: boolean
}>()

const emit = defineEmits<{
  select: [file: File]
}>()

const {t} = useI18n()
</script>

<template>
  <div class="space-y-2" data-testid="pdf-upload">
    <MutedText v-if="!canUpload" size="sm" tag="p">{{ t('documentTemplates.pdfSaveFirst') }}</MutedText>
    <template v-else>
      <FileInput accept=".pdf,application/pdf" :label="t(pdf ? 'documentTemplates.pdfReplace' : 'documentTemplates.pdfUpload')"
                 @select="file => emit('select', file)"/>
      <MutedText v-if="pdf" size="sm" tag="p">
        {{ t('documentTemplates.pdfCurrent', {name: pdf.fileName, pages: pdf.inspection.pages.length, date: formatDateTime(pdf.uploadedAt)}) }}
      </MutedText>
      <MutedText v-if="pdf" size="sm" tag="p">{{ t('documentTemplates.pdfReplaceHint') }}</MutedText>
    </template>
  </div>
</template>
