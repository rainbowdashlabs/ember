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
import DownloadButton from '@/components/button/DownloadButton.vue'
import {formatSize} from '@/util/format'
import {downloadAuthed} from '@/util/downloadAuthed'
import {events} from '@/api'
import type {FederatedEventAttachment} from '@/api/events'

/**
 * What a partner station's event hands over.
 *
 * <p>Only the files that are not kept back reach another station, and the owning station is asked
 * again before any bytes are handed over: what is listed here is what it was willing to list.
 */
const props = defineProps<{
  stationUid: string
  eventId: number
}>()

const {t} = useI18n()

const attachments = ref<FederatedEventAttachment[]>([])

async function load() {
  try {
    attachments.value = await events.listFederatedEventAttachments(props.stationUid, props.eventId)
  } catch {
    attachments.value = []
  }
}

function download(attachment: FederatedEventAttachment) {
  return downloadAuthed(
      events.federatedEventAttachmentUrl(props.stationUid, props.eventId, attachment.id),
      attachment.fileName,
  )
}

onMounted(load)
watch(() => [props.stationUid, props.eventId], load)
</script>

<template>
  <NeutralContainer v-if="attachments.length > 0" class="space-y-3" data-testid="federated-event-attachments">
    <SubHeader>{{ t('events.attachments.title') }}</SubHeader>
    <div
        v-for="attachment in attachments"
        :key="attachment.id"
        class="flex flex-wrap items-center gap-2"
        data-testid="federated-event-attachment"
    >
      <font-awesome-icon :icon="['fas', 'paperclip']" class="text-primary"/>
      <span class="text-sm">{{ attachment.name }}</span>
      <span class="text-xs text-(--text-muted)">{{ formatSize(attachment.fileSize) }}</span>
      <DownloadButton data-testid="federated-event-attachment-download" @click="download(attachment)"/>
    </div>
  </NeutralContainer>
</template>
