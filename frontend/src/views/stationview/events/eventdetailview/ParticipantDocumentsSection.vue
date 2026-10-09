/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {ParticipantDocuments, PartnerSigner, RequiredTemplate} from '@/api/generated/schema'
import FoldButton from './FoldButton.vue'
import DocumentGroupPanel from './DocumentGroupPanel.vue'
import {DOCUMENT_GROUPS, DocumentGroup, groupEntries} from './documentGroups'
import {SECTION_FOLD, loadFoldChoices, saveFoldChoices, type Fold} from './documentGroupMemory'

/**
 * Where every participant of the date stands with one document, for whoever manages the registrations, the
 * members of partner stations included. Everybody is sorted into what needs the manager now, what is still
 * missing and only needs waiting for, and what is done, each group folding under a header that counts it.
 *
 * <p>The section starts open, and of its groups only the one that needs the manager, and only while it
 * holds anybody. Whatever the reader opens or closes is remembered in the browser and holds for every
 * appointment.
 */
const props = defineProps<{
  eventId: number
  /** The date of the appointment on screen. */
  date: string
  template: RequiredTemplate
  /** Every participant of the date with their copies. */
  participants: ParticipantDocuments[]
  /** The members partner stations registered, with where they stand with every document partners sign. */
  partnerSigners: PartnerSigner[]
  /** Called once anybody's document changed, so the documents are read again. */
  onChanged: () => void
}>()

const {t} = useI18n()

const choices = ref(loadFoldChoices())
const grouped = computed(() => groupEntries(props.participants, props.partnerSigners, props.template.templateId))
const nobody = computed(() => DOCUMENT_GROUPS.every(group => grouped.value[group].length === 0))

function opensByDefault(fold: Fold): boolean {
  if (fold === SECTION_FOLD) return true
  return fold === DocumentGroup.TODO && grouped.value.todo.length > 0
}

function isOpen(fold: Fold): boolean {
  return choices.value[fold] ?? opensByDefault(fold)
}

function choose(fold: Fold, open: boolean) {
  choices.value = {...choices.value, [fold]: open}
  saveFoldChoices(choices.value)
}
</script>

<template>
  <NeutralContainer class="space-y-4" data-testid="participant-documents">
    <SubHeader>
      <FoldButton :model-value="isOpen(SECTION_FOLD)" data-testid="participant-documents-toggle"
                  @update:model-value="open => choose(SECTION_FOLD, open)">
        {{ t('events.documents.review.title', {name: template.name}) }}
      </FoldButton>
    </SubHeader>
    <template v-if="isOpen(SECTION_FOLD)">
      <MutedText size="sm" tag="p">{{ t('events.documents.review.hint') }}</MutedText>
      <MutedText v-if="nobody" size="sm" tag="p">{{ t('events.documents.review.empty') }}</MutedText>
      <DocumentGroupPanel v-for="group in DOCUMENT_GROUPS" :key="group" :model-value="isOpen(group)"
                          :event-id="eventId" :date="date" :group="group" :entries="grouped[group]"
                          :on-changed="onChanged" @update:model-value="open => choose(group, open)"/>
    </template>
  </NeutralContainer>
</template>
