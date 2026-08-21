/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import IconButton from '@/components/button/IconButton.vue'
import MediaBrowseButton from '@/components/media/MediaBrowseButton.vue'
import {formatSize} from '@/util/format'
import type {StationFile} from '@/api/media'
import type {AttachmentDraft} from './useNewsAttachments'

const attachments = defineModel<AttachmentDraft[]>('attachments', {required: true})

defineProps<{
  stationUid: string
}>()

const emit = defineEmits<{
  (e: 'add', file: StationFile): void
  (e: 'remove', index: number): void
  (e: 'move', index: number, delta: number): void
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-3">
    <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2">
      <div>
        <SubHeader>{{ t('news.attachments') }}</SubHeader>
        <p class="text-xs text-(--text-muted)">{{ t('news.attachmentsHint') }}</p>
      </div>
      <MediaBrowseButton
          :station-uid="stationUid"
          :label="t('news.attachmentAdd')"
          @pick="p => emit('add', p.file)"
      />
    </div>

    <p v-if="attachments.length === 0" class="text-sm text-(--text-muted)">
      {{ t('news.attachmentsEmpty') }}
    </p>

    <ul v-else class="space-y-2">
      <li
          v-for="(attachment, index) in attachments"
          :key="attachment.fileId"
          class="flex flex-col sm:flex-row sm:items-center gap-2 rounded-lg border border-(--border) p-2"
      >
        <div class="flex-1 min-w-0">
          <p class="text-sm truncate">{{ attachment.fileName }}</p>
          <p class="text-xs text-(--text-muted)">{{ formatSize(attachment.fileSize) }}</p>
        </div>
        <TextInput
            v-model="attachment.label"
            :placeholder="t('news.attachmentLabelPlaceholder')"
            class="sm:w-64 !text-sm"
        />
        <div class="flex items-center gap-1">
          <IconButton
              :icon="['fas', 'arrow-up']"
              :label="t('common.moveUp')"
              :disabled="index === 0"
              @click="emit('move', index, -1)"
          />
          <IconButton
              :icon="['fas', 'arrow-down']"
              :label="t('common.moveDown')"
              :disabled="index === attachments.length - 1"
              @click="emit('move', index, 1)"
          />
          <IconButton
              :icon="['fas', 'trash']"
              :label="t('news.attachmentRemove')"
              @click="emit('remove', index)"
          />
        </div>
      </li>
    </ul>
  </NeutralContainer>
</template>
