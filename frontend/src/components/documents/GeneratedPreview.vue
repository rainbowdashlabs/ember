/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import Alert from '@/components/feedback/Alert.vue'
import DocumentPdfFrame from '@/components/documents/DocumentPdfFrame.vue'
import {missingLabels} from './generation'
import {pdfOf} from '@/api/documentTemplates'
import type {PreviewResponse} from '@/api/generated/schema'

/**
 * A document drawn from a template before it is generated, with the data it still lacks.
 *
 * <p>A placeholder without a value prints as a line to fill in by hand on a letter and stays empty on
 * a filled-in PDF, so the document can still be generated; the warning says which data is missing, so
 * it can be entered in the profile first. Characters no font can print are named too, since the
 * document leaves them out.
 */
const props = defineProps<{
  preview: PreviewResponse
  title: string
}>()

const {t} = useI18n()

const pdf = computed(() => pdfOf(props.preview))
const missing = computed(() => missingLabels(props.preview.missing))
</script>

<template>
  <div class="space-y-3">
    <Alert v-if="preview.missing.length > 0" variant="error" data-testid="generated-missing">
      {{ t('documentTemplates.missingValues', {values: missing}) }}
    </Alert>
    <Alert v-if="preview.unprintable.length > 0" variant="error" data-testid="generated-unprintable">
      {{ t('documentTemplates.unprintable', {characters: preview.unprintable.join(' ')}) }}
    </Alert>
    <DocumentPdfFrame :pdf="pdf" :title="title" class="h-[70vh]" data-testid="generated-preview"/>
  </div>
</template>
