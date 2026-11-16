/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import {appointmentDocuments} from '@/api'
import type {RequiredTemplate} from '@/api/generated/schema'
import EventAttachmentsPanel from '../eventdetailview/EventAttachmentsPanel.vue'
import {eventGroupAttachmentReader} from '../eventdetailview/attachmentReaders'

/**
 * What an event group asks every part's participants to bring and the files it hands over on every
 * part. The filled-in documents belong to one date each, so they are found on each part's page.
 */
const props = defineProps<{
  eventGroupId: number
}>()

const {t} = useI18n()
const documents = ref<RequiredTemplate[]>([])
const reader = computed(() => eventGroupAttachmentReader(props.eventGroupId))

watch(() => props.eventGroupId, async id => {
  documents.value = await appointmentDocuments.listRequirements({kind: 'events/groups', id}).catch(() => [])
}, {immediate: true})
</script>

<template>
  <NeutralContainer v-if="documents.length > 0" class="space-y-2" data-testid="event-group-documents">
    <SubHeader>{{ t('eventGroups.documentsToBring') }}</SubHeader>
    <ul class="list-disc ps-5 text-sm">
      <li v-for="document in documents" :key="document.templateId" data-testid="event-group-document">{{ document.name }}</li>
    </ul>
    <MutedText tag="p" size="sm">{{ t('eventGroups.documentsToBringHint') }}</MutedText>
  </NeutralContainer>
  <EventAttachmentsPanel :reader="reader"/>
</template>
