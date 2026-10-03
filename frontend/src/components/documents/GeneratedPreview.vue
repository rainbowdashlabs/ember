/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import Alert from '@/components/feedback/Alert.vue'
import FileView from '@/components/documents/FileView.vue'
import {pdfOf} from '@/api/documentTemplates'
import type {PreviewResponse} from '@/api/generated/schema'

/**
 * A document drawn from a template before it is generated, with the data it still lacks.
 *
 * <p>A placeholder without a value prints as a line to fill in by hand, so the document can still be
 * generated; the warning says which data is missing, so it can be entered in the profile first.
 */
const props = defineProps<{
  preview: PreviewResponse
  title: string
}>()

const {t} = useI18n()

const pdf = computed(() => pdfOf(props.preview))
const missing = computed(() => props.preview.missing.map(value => value.label).join(', '))
</script>

<template>
  <div class="space-y-3">
    <Alert v-if="preview.missing.length > 0" variant="error" data-testid="generated-missing">
      {{ t('documentTemplates.missingValues', {values: missing}) }}
    </Alert>
    <div class="h-[70vh] rounded-lg border border-(--border) overflow-hidden" data-testid="generated-preview">
      <FileView :source="pdf" :title="title" mime-type="application/pdf"/>
    </div>
  </div>
</template>
