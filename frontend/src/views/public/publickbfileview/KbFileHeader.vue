/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {KbFile} from '@/api/knowledgeBase'
import {formatDateTime} from '@/util/format'
import {isPdfExportable} from '@/util/kbFileExport'
import * as publicKb from '@/api/publicKb'

const props = defineProps<{
  file: KbFile
  stationUid: string
}>()

const emit = defineEmits<{
  (e: 'back'): void
}>()

const {t} = useI18n()

const pdfUrl = computed(() => publicKb.pdfExportUrl(props.stationUid, props.file.id))
</script>

<template>
  <ButtonRow class="mb-4">
    <SecondaryButton @click="emit('back')">
      <font-awesome-icon :icon="['fas', 'chevron-left']"/>
      {{ t('publicKb.backToBrowse') }}
    </SecondaryButton>
    <SectionHeader class="text-xl font-bold sm:flex-1">{{ props.file.name }}</SectionHeader>
    <a v-if="isPdfExportable(props.file.fileType)" :href="pdfUrl" download>
      <SecondaryButton>
        <font-awesome-icon :icon="['fas', 'file-pdf']"/>
        {{ t('kb.downloadPdf') }}
      </SecondaryButton>
    </a>
  </ButtonRow>

  <MutedText tag="p" size="sm" v-if="props.file.description">
    {{ props.file.description }}
  </MutedText>

  <p v-if="props.file.updatedAt" class="text-xs text-[var(--text-muted)] mb-4">
    {{ formatDateTime(props.file.updatedAt) }}
  </p>
</template>
