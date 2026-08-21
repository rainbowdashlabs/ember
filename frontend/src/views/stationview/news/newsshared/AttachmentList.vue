/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import SubHeader from '@/components/typography/SubHeader.vue'
import {mediaFileUrl} from '@/api/media'
import {formatSize} from '@/util/format'
import type {NewsAttachment} from '@/api/news'

const props = defineProps<{
  attachments: NewsAttachment[]
  stationUid: string
}>()

const {t} = useI18n()

/**
 * What a reader sees: the author's label where they gave one, the file name otherwise.
 */
function displayName(attachment: NewsAttachment): string {
  return attachment.label?.trim() ? attachment.label : attachment.fileName
}

function href(attachment: NewsAttachment): string {
  return mediaFileUrl(props.stationUid, attachment.contentHash)
}
</script>

<template>
  <section v-if="attachments.length > 0" class="mt-6 space-y-2">
    <SubHeader>{{ t('news.attachments') }}</SubHeader>
    <ul class="space-y-1">
      <li v-for="attachment in attachments" :key="attachment.id">
        <a
            :href="href(attachment)"
            :download="attachment.fileName"
            class="inline-flex items-center gap-2 text-sm text-(--primary) hover:underline"
        >
          <font-awesome-icon :icon="['fas', 'paperclip']" class="w-3.5 h-3.5"/>
          <span>{{ displayName(attachment) }}</span>
          <span class="text-xs text-(--text-muted)">{{ formatSize(attachment.fileSize) }}</span>
        </a>
      </li>
    </ul>
  </section>
</template>
