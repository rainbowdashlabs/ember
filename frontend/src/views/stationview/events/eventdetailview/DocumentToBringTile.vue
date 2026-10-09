/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import BareButton from '@/components/button/BareButton.vue'
import FileThumbnail from '@/components/documents/FileThumbnail.vue'
import {toBringPictureUrl} from '@/api/appointmentDocuments'
import type {RequiredTemplate} from '@/api/generated/schema'
import DocumentPictureModal from './DocumentPictureModal.vue'

/**
 * One document an appointment asks for: a picture of its first page, which opens large, and its name. The
 * copies of the people the reader acts for are on their own tiles ({@link PersonDocumentsTile}), and where
 * every participant stands is in its own section ({@link ParticipantDocumentsSection}).
 */
const props = defineProps<{
  eventId: number
  template: RequiredTemplate
}>()

const TILE_PICTURE_SIZE = 512

const {t} = useI18n()
const enlarged = ref(false)
</script>

<template>
  <article class="flex flex-col overflow-hidden rounded-theme border border-bg-light-accent dark:border-bg-dark-accent"
           data-testid="document-to-bring-tile">
    <BareButton :title="t('documentTemplates.browse.enlarge')" class="text-left" @click="enlarged = true">
      <FileThumbnail :url="toBringPictureUrl(eventId, props.template.templateId, TILE_PICTURE_SIZE)"
                     mime-type="application/pdf" :alt="props.template.name" size="aspect-[4/3] w-full" anchor-top/>
    </BareButton>
    <span class="p-3 font-medium">{{ props.template.name }}</span>
    <DocumentPictureModal v-if="enlarged" v-model="enlarged" :event-id="eventId" :template="props.template"/>
  </article>
</template>
