/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import Alert from '@/components/feedback/Alert.vue'
import MutedText from '@/components/typography/MutedText.vue'
import DocumentPdfFrame from '@/components/documents/DocumentPdfFrame.vue'
import {pdfOf} from '@/api/documentTemplates'
import type {BulkPreviewResponse} from '@/api/generated/schema'
import {gapText} from './bulkGeneration'

/**
 * The look before a run: how many members it generates for, the document of the first member whose
 * document can be drawn, and every member with missing data or a document that cannot be drawn.
 */
const props = defineProps<{
  preview: BulkPreviewResponse
  templateName: string
}>()

const {t} = useI18n()

const pdf = computed(() => (props.preview.preview ? pdfOf(props.preview.preview) : null))
</script>

<template>
  <div class="space-y-3" data-testid="bulk-preview-summary">
    <MutedText size="sm" tag="p">{{ t('documentTemplates.bulk.memberCount', {count: preview.memberCount}) }}</MutedText>
    <Alert v-if="preview.gaps.length > 0" variant="error" data-testid="bulk-gaps">
      <p>{{ t('documentTemplates.bulk.gapsIntro', {count: preview.gaps.length}) }}</p>
      <ul class="mt-2 list-disc pl-5 text-sm">
        <li v-for="gap in preview.gaps" :key="gap.memberId">{{ gap.name }}: {{ gapText(gap, t) }}</li>
      </ul>
    </Alert>
    <DocumentPdfFrame v-if="pdf" :pdf="pdf" :title="templateName" class="h-[60vh]"/>
  </div>
</template>
