/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import FileThumbnail from '@/components/documents/FileThumbnail.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {DocumentTemplateSummary} from '@/api/generated/schema'
import {formatDate} from '@/util/format'
import TemplateBadges from './TemplateBadges.vue'

/**
 * What a template tile shows: the first page drawn without a member, from its top where a document
 * says what it is, then its name, its badges, its version and when it was last used.
 */
defineProps<{
  template: DocumentTemplateSummary
  /** Where the picture of the template is served. */
  pictureUrl: string
}>()

const {t} = useI18n()
</script>

<template>
  <FileThumbnail :url="pictureUrl" mime-type="application/pdf" :alt="template.name" size="aspect-[4/3] w-full"
                 anchor-top/>
  <div class="space-y-2 p-3">
    <div class="truncate text-sm font-medium" :title="template.name">{{ template.name }}</div>
    <TemplateBadges :template="template"/>
    <MutedText size="sm">
      {{ t('documentTemplates.version', {version: template.version}) }} ·
      {{ template.lastUsedAt ? t('documentTemplates.browse.lastUsed', {date: formatDate(template.lastUsedAt)}) : t('documentTemplates.browse.neverUsed') }}
    </MutedText>
  </div>
</template>
