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
import HelpList from '@/components/helpcenter/HelpList.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import IconButton from '@/components/button/IconButton.vue'
import {LinkType} from '@/api/generated/schema'
import TicketCreateMainColumn from '@/views/stationview/boards/ticketcreateview/TicketCreateMainColumn.vue'
import TicketCreateRightColumn from '@/views/stationview/boards/ticketcreateview/TicketCreateRightColumn.vue'
import {ticketCreateFixtures} from './ticketcreatehelp/fixtures'

const {t} = useI18n()
const fixture = ticketCreateFixtures(t)
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

    <HelpSection :title="t('helpCenter.ticketCreate.exampleTitle')">
      <NeutralContainer>
        <div class="flex items-center gap-3 mb-4">
          <IconButton :icon="['fas', 'chevron-left']" :label="t('common.back')" />
          <SectionHeader>{{ t('boards.createTicket') }}</SectionHeader>
          <span class="text-xs font-mono text-(--text-muted) bg-(--bg-accent) px-1.5 py-0.5 rounded">{{ fixture.shortKey }}</span>
        </div>
        <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
          <TicketCreateMainColumn
              :title="fixture.title" :description="fixture.description" :checklist-items="fixture.checklistItems"
              new-checklist-title="" :weblinks="fixture.weblinks" new-weblink-url="" new-weblink-title=""
              :ticket-links="fixture.ticketLinks" new-link-ticket-id="" :new-link-type="LinkType.RELATES_TO"
              :all-tickets="fixture.allTickets" :short-key="fixture.shortKey"
          />
          <TicketCreateRightColumn
              :lane-id="fixture.laneId" :priority="fixture.priority" :assignee="fixture.assignee" :due-date="fixture.dueDate"
              :create-lane-options="fixture.lanes" :assignable-members="fixture.members" :all-labels="fixture.labels"
              :selected-labels="fixture.selectedLabels" :failure="null" validation-error="" :submitting="false"
              cancel-to=""
          />
        </div>
      </NeutralContainer>
    </HelpSection>

    <HelpSection :title="t('helpCenter.ticketCreate.startLaneTitle')">
      <p>{{ t('helpCenter.ticketCreate.startLaneText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.ticketCreate.linkTypesTitle')">
      <p>{{ t('helpCenter.ticketCreate.linkTypesText') }}</p>
      <HelpList>
        <li><strong>{{ t('boards.linkRelatesTo') }}:</strong> {{ t('helpCenter.ticketCreate.linkRelatesDesc') }}</li>
        <li><strong>{{ t('boards.linkBlocks') }} / {{ t('boards.linkBlockedBy') }}:</strong> {{ t('helpCenter.ticketCreate.linkBlocksDesc') }}</li>
        <li><strong>{{ t('boards.linkCauses') }} / {{ t('boards.linkCausedBy') }}:</strong> {{ t('helpCenter.ticketCreate.linkCausesDesc') }}</li>
      </HelpList>
    </HelpSection>

    <HelpSection :title="t('helpCenter.ticketCreate.mentionsTitle')">
      <p>{{ t('helpCenter.ticketCreate.mentionsText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.ticketCreate.tip') }}</HelpTip>
  </HelpArticle>
</template>
