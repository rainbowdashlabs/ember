/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import RequirementStatusBadge from './RequirementStatusBadge.vue'
import type {ParticipantCopy} from './documentTiles'

/** Where every participant of the date stands with one document, for an event manager. */
const open = defineModel<boolean>({required: true})

defineProps<{
  /** The document's name. */
  title: string
  participants: ParticipantCopy[]
}>()

const {t} = useI18n()
</script>

<template>
  <Modal v-model="open" size="md">
    <div class="space-y-3" data-testid="documents-to-bring-overview">
      <SubHeader>{{ t('events.documents.overviewOf', {name: title}) }}</SubHeader>
      <MutedText v-if="participants.length === 0" size="sm" tag="p">{{ t('events.documents.overviewEmpty') }}</MutedText>
      <ul v-else class="space-y-2">
        <li v-for="participant in participants" :key="participant.memberId" class="flex items-center gap-2"
            data-testid="documents-to-bring-participant">
          <span class="flex-1">{{ participant.name }}</span>
          <RequirementStatusBadge :document="participant.document"/>
        </li>
      </ul>
    </div>
  </Modal>
</template>
