/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import BareButton from '@/components/button/BareButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import FileThumbnail from '@/components/documents/FileThumbnail.vue'
import {toBringPictureUrl} from '@/api/appointmentDocuments'
import DocumentDownload from './DocumentDownload.vue'
import DocumentPictureModal from './DocumentPictureModal.vue'
import ParticipantStatusModal from './ParticipantStatusModal.vue'
import type {DocumentTile, ParticipantCopy} from './documentTiles'

/**
 * One document an appointment asks for: a picture of its first page, which opens large, its name,
 * and a download and a scan hand-in per participant the reader acts for. An event manager also gets a
 * button showing where every participant stands with it, where scans are checked.
 */
const props = defineProps<{
  eventId: number
  /** The date of the appointment on screen. */
  date: string
  tile: DocumentTile
  busy: boolean
  /** Called once a scan was confirmed, turned down or handed in from the overview. */
  onChanged: () => void
}>()

const emit = defineEmits<{
  fetch: [copy: ParticipantCopy]
  handIn: [copy: ParticipantCopy, file: File]
}>()

const TILE_PICTURE_SIZE = 512

const {t} = useI18n()
const enlarged = ref(false)
const showingStatus = ref(false)
</script>

<template>
  <article class="flex flex-col overflow-hidden rounded-theme border border-bg-light-accent dark:border-bg-dark-accent"
           data-testid="document-to-bring-tile">
    <BareButton :title="t('documentTemplates.browse.enlarge')" class="text-left" @click="enlarged = true">
      <FileThumbnail :url="toBringPictureUrl(eventId, props.tile.template.templateId, TILE_PICTURE_SIZE)"
                     mime-type="application/pdf" :alt="props.tile.template.name" size="aspect-[4/3] w-full" anchor-top/>
    </BareButton>
    <div class="flex flex-1 flex-col gap-2 p-3">
      <div class="flex items-start gap-2">
        <span class="flex-1 font-medium">{{ props.tile.template.name }}</span>
        <IconButton v-if="props.tile.participants" :icon="['fas', 'users']" :label="t('events.documents.overviewTitle')"
                    data-testid="document-to-bring-status" @click="showingStatus = true"/>
      </div>
      <DocumentDownload v-for="copy in props.tile.own" :key="copy.memberId" :copy="copy"
                        :named="props.tile.own.length > 1" :busy="busy" @fetch="emit('fetch', copy)"
                        @hand-in="file => emit('handIn', copy, file)"/>
    </div>
    <DocumentPictureModal v-if="enlarged" v-model="enlarged" :event-id="eventId" :template="props.tile.template"/>
    <ParticipantStatusModal v-if="showingStatus && props.tile.participants" v-model="showingStatus"
                            :event-id="eventId" :date="date" :template="props.tile.template"
                            :participants="props.tile.participants" :on-changed="onChanged"/>
  </article>
</template>
