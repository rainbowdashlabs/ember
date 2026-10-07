/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import ButtonRow from '@/components/button/ButtonRow.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {WebFontView} from '@/api/generated/schema'
import {formatDate, formatSize} from '@/util/format'

/**
 * Whether a style has a web version, which the template editor shows it in instead of the file documents
 * print with, and the buttons that upload, replace or remove it.
 */
defineProps<{
  web: WebFontView | null
}>()

const emit = defineEmits<{
  upload: []
  remove: []
}>()

const {t} = useI18n()
</script>

<template>
  <div class="flex flex-wrap items-center justify-between gap-2" data-testid="web-font">
    <MutedText v-if="web" size="sm" class="min-w-0 truncate">
      {{ t('documentFonts.webLine', {name: web.fileName, size: formatSize(web.sizeBytes), date: formatDate(web.uploadedAt)}) }}
    </MutedText>
    <MutedText v-else size="sm">{{ t('documentFonts.webNone') }}</MutedText>
    <ButtonRow v-if="web" pair align="end">
      <SecondaryButton :icon="['fas', 'upload']" data-testid="web-font-replace" @click="emit('upload')">
        {{ t('documentFonts.webReplace') }}
      </SecondaryButton>
      <SecondaryButton :icon="['fas', 'trash']" data-testid="web-font-remove" @click="emit('remove')">
        {{ t('documentFonts.webRemove') }}
      </SecondaryButton>
    </ButtonRow>
    <SecondaryButton v-else :icon="['fas', 'upload']" data-testid="web-font-upload" @click="emit('upload')">
      {{ t('documentFonts.webUpload') }}
    </SecondaryButton>
  </div>
</template>
