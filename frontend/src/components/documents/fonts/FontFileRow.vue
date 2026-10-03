/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import DeleteButton from '@/components/button/DeleteButton.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import MutedText from '@/components/typography/MutedText.vue'
import {FontOutline, type DocumentFontView} from '@/api/generated/schema'
import {formatDate, formatSize} from '@/util/format'

/** One style of a family: which it is, the file it came from, and whether fields on a PDF can print it. */
defineProps<{
  font: DocumentFontView
}>()

const emit = defineEmits<{
  remove: []
}>()

const {t} = useI18n()
</script>

<template>
  <li class="flex flex-wrap items-center justify-between gap-2 py-2" data-testid="font-file">
    <div class="flex min-w-0 flex-wrap items-center gap-2">
      <SecondaryBadge>{{ t(`documentFonts.style.${font.style}`) }}</SecondaryBadge>
      <InfoBadge v-if="font.outline === FontOutline.CFF">{{ t('documentFonts.letterOnly') }}</InfoBadge>
      <MutedText size="sm" class="truncate">
        {{ t('documentFonts.fileLine', {name: font.fileName, size: formatSize(font.sizeBytes), date: formatDate(font.uploadedAt)}) }}
      </MutedText>
    </div>
    <DeleteButton data-testid="font-delete" @click="emit('remove')"/>
  </li>
</template>
