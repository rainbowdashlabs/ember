/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import HelpTip from '@/components/helpcenter/HelpTip.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import MemberSelectInput from '@/components/input/select/MemberSelectInput.vue'
import DateInput from '@/components/input/datetime/DateInput.vue'
import MarkdownEditor from '@/components/input/MarkdownEditor.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import IconButton from '@/components/button/IconButton.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'

const {t} = useI18n()

const dummyMembers = [
    {id: 1, memberUid: 'u1', stationUid: 's1', name: 'Max Mustermann'},
    {id: 2, memberUid: 'u2', stationUid: 's1', name: 'Lisa Schmidt'},
    {id: 3, memberUid: 'u3', stationUid: 's1', name: 'Tom Müller'},
]
</script>

<template>
  <HelpArticle :title="t('helpCenter.ticketCreate.title')" :subtitle="t('helpCenter.ticketCreate.subtitle')">
    <HelpSection :title="t('helpCenter.ticketCreate.whatIs')">
      <p>{{ t('helpCenter.ticketCreate.whatIsText') }}</p>
      <p>{{ t('helpCenter.ticketCreate.whatIsText2') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.ticketCreate.formTitle')">
      <p>{{ t('helpCenter.ticketCreate.formText') }}</p>
    </HelpSection>

    <!-- Dummy UI: Two-column create form -->
    <HelpSection :title="t('helpCenter.ticketCreate.exampleTitle')">
      <NeutralContainer>
        <div class="flex items-center gap-3 mb-4">
          <IconButton :icon="['fas', 'chevron-left']" label="Back" />
          <SectionHeader>{{ t('boards.createTicket') }}</SectionHeader>
          <span class="text-xs font-mono text-(--text-muted) bg-(--bg-accent) px-1.5 py-0.5 rounded">JF</span>
        </div>

        <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
          <!-- Left column -->
          <div class="lg:col-span-2 space-y-4">
            <div>
              <FieldLabel class="mb-1">{{ t('boards.ticketTitle') }} *</FieldLabel>
              <TextInput model-value="Ausrüstung für Wettkampf prüfen" />
            </div>

            <div>
              <FieldLabel class="mb-1">{{ t('boards.ticketDescription') }}</FieldLabel>
              <MarkdownEditor model-value="Alle Helme und Jacken auf Vollständigkeit prüfen. @Lisa bitte die Schläuche kontrollieren." :placeholder="t('boards.ticketDescription')" />
            </div>

            <!-- Checklist -->
            <NeutralContainer>
              <SubHeader class="mb-2">{{ t('boards.checklist') }}</SubHeader>
              <div class="flex items-center gap-2 py-0.5">
                <CheckboxInput :model-value="true" />
                <span class="flex-1 text-sm line-through text-(--text-muted)">{{ t('helpCenter.ticketCreate.checkItem1') }}</span>
                <IconButton :icon="['fas', 'xmark']" label="Remove" class="text-xs" />
              </div>
              <div class="flex items-center gap-2 py-0.5">
                <CheckboxInput :model-value="false" />
                <span class="flex-1 text-sm">{{ t('helpCenter.ticketCreate.checkItem2') }}</span>
                <IconButton :icon="['fas', 'xmark']" label="Remove" class="text-xs" />
              </div>
              <div class="flex gap-2 mt-2 items-center">
                <TextInput model-value="" :placeholder="t('boards.addChecklistItem')" class="flex-1 text-sm" />
                <IconButton :icon="['fas', 'plus']" :label="t('common.add')" class="text-(--text-muted)" />
              </div>
            </NeutralContainer>

            <!-- Weblinks -->
            <NeutralContainer>
              <SubHeader class="mb-2">{{ t('boards.weblinks') }}</SubHeader>
              <div class="flex items-center gap-2 py-0.5">
                <font-awesome-icon :icon="['fas', 'globe']" class="text-(--text-muted) text-xs shrink-0" />
                <span class="text-sm truncate flex-1">{{ t('helpCenter.ticketCreate.weblinkExample') }}</span>
                <IconButton :icon="['fas', 'xmark']" label="Remove" class="text-xs" />
              </div>
              <div class="flex gap-2 mt-2 items-center">
                <TextInput model-value="" placeholder="https://..." class="flex-1 text-sm" />
                <TextInput model-value="" :placeholder="t('boards.weblinkTitle')" class="flex-1 text-sm" />
                <IconButton :icon="['fas', 'plus']" :label="t('common.add')" class="text-(--text-muted)" />
              </div>
            </NeutralContainer>

            <!-- Ticket links -->
            <NeutralContainer>
              <SubHeader class="mb-2">{{ t('boards.links') }}</SubHeader>
              <div class="flex items-center gap-2 py-0.5">
                <font-awesome-icon :icon="['fas', 'link']" class="text-(--text-muted) text-xs shrink-0" />
                <span class="text-xs text-(--text-muted)">{{ t('boards.linkBlockedBy') }}</span>
                <span class="text-sm truncate flex-1">JF-12 {{ t('helpCenter.ticketCreate.linkedTicketExample') }}</span>
                <IconButton :icon="['fas', 'xmark']" label="Remove" class="text-xs" />
              </div>
              <div class="flex gap-2 mt-2 items-center flex-wrap">
                <SelectInput model-value="RELATES_TO" class="w-40">
                  <option value="RELATES_TO">{{ t('boards.linkRelatesTo') }}</option>
                  <option value="BLOCKS">{{ t('boards.linkBlocks') }}</option>
                  <option value="BLOCKED_BY">{{ t('boards.linkBlockedBy') }}</option>
                  <option value="CAUSES">{{ t('boards.linkCauses') }}</option>
                  <option value="CAUSED_BY">{{ t('boards.linkCausedBy') }}</option>
                </SelectInput>
                <SelectInput model-value="" class="flex-1 min-w-0">
                  <option value="">{{ t('boards.linkedTickets') }}...</option>
                  <option value="1">JF-10 Schläuche bestellen</option>
                  <option value="2">JF-11 Helme sortieren</option>
                </SelectInput>
                <IconButton :icon="['fas', 'plus']" :label="t('common.add')" class="text-(--text-muted)" />
              </div>
            </NeutralContainer>
          </div>

          <!-- Right column -->
          <div class="space-y-4">
            <div>
              <FieldLabel class="mb-1">{{ t('boards.lanes') }}</FieldLabel>
              <SelectInput model-value="1" class="w-full">
                <option value="1">Backlog</option>
                <option value="2">{{ t('helpCenter.ticketCreate.laneToDo') }}</option>
              </SelectInput>
            </div>

            <div>
              <FieldLabel class="mb-1">{{ t('boards.priority') }}</FieldLabel>
              <SelectInput model-value="HIGH" class="w-full">
                <option value="LOWEST">{{ t('boards.priorityLowest') }}</option>
                <option value="LOW">{{ t('boards.priorityLow') }}</option>
                <option value="MEDIUM">{{ t('boards.priorityMedium') }}</option>
                <option value="HIGH">{{ t('boards.priorityHigh') }}</option>
                <option value="HIGHEST">{{ t('boards.priorityHighest') }}</option>
              </SelectInput>
            </div>

            <div>
              <FieldLabel class="mb-1">{{ t('boards.assignee') }}</FieldLabel>
              <MemberSelectInput model-value="1" :members="dummyMembers" :placeholder="t('boards.unassigned')" />
            </div>

            <div>
              <FieldLabel class="mb-1">{{ t('boards.dueDate') }}</FieldLabel>
              <DateInput model-value="2026-06-20" />
            </div>

            <div class="flex gap-2">
              <SecondaryButton class="flex-1">{{ t('common.cancel') }}</SecondaryButton>
              <PrimaryButton class="flex-1">{{ t('common.create') }}</PrimaryButton>
            </div>
          </div>
        </div>
      </NeutralContainer>
    </HelpSection>

    <HelpSection :title="t('helpCenter.ticketCreate.startLaneTitle')">
      <p>{{ t('helpCenter.ticketCreate.startLaneText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.ticketCreate.linkTypesTitle')">
      <p>{{ t('helpCenter.ticketCreate.linkTypesText') }}</p>
      <ul class="list-disc ml-4 space-y-1">
        <li><strong>{{ t('boards.linkRelatesTo') }}:</strong> {{ t('helpCenter.ticketCreate.linkRelatesDesc') }}</li>
        <li><strong>{{ t('boards.linkBlocks') }} / {{ t('boards.linkBlockedBy') }}:</strong> {{ t('helpCenter.ticketCreate.linkBlocksDesc') }}</li>
        <li><strong>{{ t('boards.linkCauses') }} / {{ t('boards.linkCausedBy') }}:</strong> {{ t('helpCenter.ticketCreate.linkCausesDesc') }}</li>
      </ul>
    </HelpSection>

    <HelpSection :title="t('helpCenter.ticketCreate.mentionsTitle')">
      <p>{{ t('helpCenter.ticketCreate.mentionsText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.ticketCreate.tip') }}</HelpTip>
  </HelpArticle>
</template>
