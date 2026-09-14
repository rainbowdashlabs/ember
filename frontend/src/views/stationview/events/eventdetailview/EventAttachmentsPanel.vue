/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import Alert from '@/components/feedback/Alert.vue'
import DownloadButton from '@/components/button/DownloadButton.vue'
import {formatSize} from '@/util/format'
import {downloadAuthed} from '@/util/downloadAuthed'
import {events} from '@/api'
import type {EventAttachment} from '@/api/events'

/**
 * What an event hands over, as far as this reader may have it.
 *
 * <p>The server answers only the files the reader is allowed, so nothing is hidden here: a panel
 * that filtered would be a second opinion about a question already answered. An internal file is
 * marked as such for the people who do get it, so nobody hands the room a sheet meant for the crew.
 */
const props = defineProps<{
  eventId: number
}>()

const {t} = useI18n()

const attachments = ref<EventAttachment[]>([])
const failed = ref(false)

async function load() {
  failed.value = false
  try {
    attachments.value = await events.listEventAttachments(props.eventId)
  } catch {
    failed.value = true
  }
}

/** Shown by its label, saved under its own file name, which is the half that carries the type. */
function download(attachment: EventAttachment) {
  return downloadAuthed(events.eventAttachmentUrl(props.eventId, attachment.id), attachment.fileName)
}

onMounted(load)
watch(() => props.eventId, load)
</script>

<template>
  <NeutralContainer v-if="failed" class="space-y-3" data-testid="event-attachment-failure">
    <SubHeader>{{ t('events.attachments.title') }}</SubHeader>
    <Alert variant="error">{{ t('events.attachments.loadFailed') }}</Alert>
  </NeutralContainer>

  <NeutralContainer v-else-if="attachments.length > 0" class="space-y-3" data-testid="event-attachment-list">
    <SubHeader>{{ t('events.attachments.title') }}</SubHeader>
    <div
        v-for="attachment in attachments"
        :key="attachment.id"
        :data-attachment="attachment.id"
        class="flex flex-wrap items-center gap-2"
        data-testid="event-attachment-row"
    >
      <font-awesome-icon :icon="['fas', 'paperclip']" class="text-primary"/>
      <span class="text-sm">{{ attachment.label?.trim() || attachment.fileName }}</span>
      <span class="text-xs text-(--text-muted)">{{ formatSize(attachment.fileSize) }}</span>
      <InfoBadge v-if="attachment.internal" data-testid="event-attachment-internal-badge">
        {{ t('events.attachments.internal') }}
      </InfoBadge>
      <DownloadButton data-testid="event-attachment-download" @click="download(attachment)"/>
    </div>
  </NeutralContainer>
</template>
