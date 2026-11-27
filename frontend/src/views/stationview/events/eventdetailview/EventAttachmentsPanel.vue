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
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import DownloadButton from '@/components/button/DownloadButton.vue'
import BareButton from '@/components/button/BareButton.vue'
import FileThumbnail from '@/components/documents/FileThumbnail.vue'
import FilePreviewModal from '@/components/documents/FilePreviewModal.vue'
import {formatSize} from '@/util/format'
import {downloadAuthed} from '@/util/downloadAuthed'
import type {EventAttachment} from '@/api/generated/schema'
import type {AttachmentReader} from './attachmentReaders'
import {describeFailure, type Failure} from '@/util/failure'

/**
 * What an event hands over, as far as this reader may have it.
 *
 * <p>The server answers only the files the reader is allowed, so nothing is hidden here: a panel
 * that filtered would be a second opinion about a question already answered. An internal file is
 * marked as such for the people who do get it, so nobody hands the room a sheet meant for the crew.
 */
const props = defineProps<{
  /** Whose files these are: an appointment's, its event group's among them, or an event group's. */
  reader: AttachmentReader
}>()

const {t} = useI18n()

const attachments = ref<EventAttachment[]>([])
const failure = ref<Failure | null>(null)

/**
 * Reads the files, and shows in the reader's terms where it could not.
 *
 * <p>The sentence names these files rather than repeating whatever the server reported, because a GET
 * that fails shows nothing a reader can use. What to do about it, and whether it is worth reporting,
 * still come from the failure itself.
 */
async function load() {
  failure.value = null
  try {
    attachments.value = await props.reader.list()
  } catch (e) {
    failure.value = {...describeFailure(e, t), message: t('events.attachments.loadFailed')}
  }
}

/** Shown by its label, saved under its own file name, which is the half that carries the type. */
function download(attachment: EventAttachment) {
  return downloadAuthed(props.reader.fileUrl(attachment.id), attachment.fileName)
}

const previewed = ref<EventAttachment | null>(null)

/** What an attachment is called where it is shown, which is its label until it has none. */
function nameOf(attachment: EventAttachment): string {
  return attachment.label?.trim() || attachment.fileName
}

onMounted(load)
watch(() => props.reader, load)
</script>

<template>
  <NeutralContainer v-if="failure" class="space-y-3" data-testid="event-attachment-failure">
    <SubHeader>{{ t('events.attachments.title') }}</SubHeader>
    <FailureAlert :failure="failure"/>
  </NeutralContainer>

  <NeutralContainer v-else-if="attachments.length > 0" class="space-y-3" data-testid="event-attachment-list">
    <SubHeader>{{ t('events.attachments.title') }}</SubHeader>
    <div
        v-for="attachment in attachments"
        :key="attachment.id"
        :data-attachment="attachment.id"
        class="flex w-full flex-wrap items-center gap-2 rounded-theme p-1 text-left hover:bg-bg-light-accent/40 dark:hover:bg-bg-dark-accent/40"
        data-testid="event-attachment-row"
    >
      <BareButton
          class="flex min-w-0 flex-1 flex-wrap items-center gap-2 text-left"
          data-testid="event-attachment-open"
          @click="previewed = attachment"
      >
        <FileThumbnail
            :url="reader.pictureUrl(attachment.id, 192)"
            :mime-type="attachment.mimeType"
            :alt="nameOf(attachment)"
        />
        <span class="text-sm">{{ nameOf(attachment) }}</span>
        <span class="text-xs text-(--text-muted)">{{ formatSize(attachment.fileSize) }}</span>
        <InfoBadge v-if="attachment.internal" data-testid="event-attachment-internal-badge">
          {{ t('events.attachments.internal') }}
        </InfoBadge>
        <SecondaryBadge v-if="attachment.fromEventGroup && reader.showsEventGroupFiles" data-testid="event-attachment-group-badge">
          {{ t('eventGroups.fromEventGroup') }}
        </SecondaryBadge>
      </BareButton>
      <DownloadButton class="ms-auto" data-testid="event-attachment-download" @click="download(attachment)"/>
    </div>

    <FilePreviewModal
        v-if="previewed"
        :source="reader.fileUrl(previewed.id)"
        :title="nameOf(previewed)"
        :mime-type="previewed.mimeType"
        @close="previewed = null"
        @download="download(previewed)"
    />
  </NeutralContainer>
</template>
