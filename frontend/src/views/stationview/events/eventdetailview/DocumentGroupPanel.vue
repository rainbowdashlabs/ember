/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import MutedText from '@/components/typography/MutedText.vue'
import FoldButton from './FoldButton.vue'
import PersonDocumentsTile from './PersonDocumentsTile.vue'
import PersonDocument from './PersonDocument.vue'
import PartnerDocument from './PartnerDocument.vue'
import type {DocumentEntry, DocumentGroup} from './documentGroups'

/**
 * One group of the people on a manager's view of a document, under a header that names it with how many
 * are in it and folds it. Open, it says what the group means and shows one tile per person, side by side
 * where the page is wide enough and one under the other on a phone.
 */
const open = defineModel<boolean>({required: true})

defineProps<{
  eventId: number
  /** The date of the appointment on screen. */
  date: string
  group: DocumentGroup
  entries: DocumentEntry[]
  /** Called once anybody's document changed, so the documents are read again. */
  onChanged: () => void
}>()

const {t} = useI18n()

function keyOf(entry: DocumentEntry): string {
  return entry.kind === 'participant' ? `member-${entry.copy.memberId}` : `partner-${entry.signer.registrationId}`
}
</script>

<template>
  <section class="space-y-3" :data-testid="`document-group-${group}`">
    <FoldButton v-model="open" :data-testid="`document-group-${group}-toggle`">
      <span class="font-medium">{{ t(`events.documents.review.group.${group}`, {count: entries.length}) }}</span>
    </FoldButton>
    <template v-if="open">
      <MutedText size="sm" tag="p">{{ t(`events.documents.review.groupHint.${group}`) }}</MutedText>
      <div v-if="entries.length > 0" class="grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-3">
        <template v-for="entry in entries" :key="keyOf(entry)">
          <PersonDocumentsTile v-if="entry.kind === 'participant'" :name="entry.copy.name"
                               :withdrawn-at="entry.withdrawnAt">
            <PersonDocument manager :event-id="eventId" :date="date" :copy="entry.copy" :busy="false"
                            @changed="onChanged"/>
          </PersonDocumentsTile>
          <PersonDocumentsTile v-else :name="entry.signer.member?.name ?? t('events.documents.partners.unknown')"
                               :station="entry.signer.member?.stationName">
            <PartnerDocument :event-id="eventId" :signer="entry.signer" :document="entry.document"
                             :on-changed="onChanged"/>
          </PersonDocumentsTile>
        </template>
      </div>
    </template>
  </section>
</template>
