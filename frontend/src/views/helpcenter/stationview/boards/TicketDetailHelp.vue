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
import SubHeader from '@/components/typography/SubHeader.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import TicketHeaderBar from '@/views/stationview/boards/ticketdetailview/TicketHeaderBar.vue'
import TicketTitleEditor from '@/views/stationview/boards/ticketdetailview/TicketTitleEditor.vue'
import TicketBody from '@/views/stationview/boards/ticketdetailview/TicketBody.vue'
import TicketAddMenu from '@/views/stationview/boards/ticketdetailview/TicketAddMenu.vue'
import {ticketDetailFixtures} from './ticketdetailhelp/fixtures'

const {t} = useI18n()
const fixture = ticketDetailFixtures(t)
</script>

<template>
  <HelpArticle :title="t('helpCenter.ticketDetail.title')" :subtitle="t('helpCenter.ticketDetail.subtitle')">
    <HelpSection :title="t('helpCenter.ticketDetail.whatIs')">
      <p>{{ t('helpCenter.ticketDetail.whatIsText') }}</p>
      <p>{{ t('helpCenter.ticketDetail.whatIsText2') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.ticketDetail.headerTitle')">
      <p>{{ t('helpCenter.ticketDetail.headerText') }}</p>
      <NeutralContainer>
        <TicketHeaderBar :short-key="fixture.board.shortKey" :ticket-number="fixture.ticket.ticketNumber" :is-watching="false" can-edit/>
        <TicketTitleEditor :title="fixture.ticket.title" :editing="false" can-edit/>
      </NeutralContainer>
    </HelpSection>

    <HelpSection :title="t('helpCenter.ticketDetail.clickToEditTitle')">
      <p>{{ t('helpCenter.ticketDetail.clickToEditText') }}</p>
      <p>{{ t('helpCenter.ticketDetail.clickToEditText2') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.ticketDetail.layoutTitle')">
      <p>{{ t('helpCenter.ticketDetail.layoutText') }}</p>
      <NeutralContainer>
        <TicketBody
            :board="fixture.board" :ticket="fixture.ticket" :all-tickets="fixture.allTickets" :lanes="fixture.lanes"
            :members="fixture.members" :assignable-members="fixture.members" :all-labels="fixture.allLabels"
            :ticket-labels="fixture.ticketLabels" :board-fields="fixture.boardFields" :priority-options="fixture.priorityChoices"
            :checklist="fixture.checklist" checklist-visible :links="fixture.links" :weblinks="fixture.weblinks"
            :attachments="fixture.attachments" :transitions="fixture.transitions" :history="fixture.history"
            :comment-source="fixture.commentSource" :kb-links="fixture.kbLinks" :kb-search-results="[]" :can-edit="false"
            :failure="null" :title="fixture.ticket.title" :description="fixture.ticket.description ?? ''"
            :priority="fixture.ticket.priority" :assigned-member-id="fixture.assignedMemberId"
            :due-date="fixture.ticket.dueDate ?? ''" :field-values="fixture.fieldValues"
            :show-add-link="false" :show-add-weblink="false" :show-kb-search="false"
        />
      </NeutralContainer>
    </HelpSection>

    <HelpSection :title="t('helpCenter.ticketDetail.addMenuTitle')">
      <p>{{ t('helpCenter.ticketDetail.addMenuText') }}</p>
      <div class="h-56">
        <TicketAddMenu can-add-checklist open/>
      </div>
    </HelpSection>

    <HelpSection :title="t('helpCenter.ticketDetail.sidebarTitle')">
      <p>{{ t('helpCenter.ticketDetail.sidebarText') }}</p>
      <p>{{ t('helpCenter.ticketDetail.sidebarLaneText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.ticketDetail.linkTypesTitle')">
      <p>{{ t('helpCenter.ticketDetail.linkTypesText') }}</p>
      <HelpList>
        <li><strong>{{ t('boards.linkRelatesTo') }}:</strong> {{ t('helpCenter.ticketDetail.linkRelatesDesc') }}</li>
        <li><strong>{{ t('boards.linkBlocks') }} / {{ t('boards.linkBlockedBy') }}:</strong> {{ t('helpCenter.ticketDetail.linkBlocksDesc') }}</li>
        <li><strong>{{ t('boards.linkCauses') }} / {{ t('boards.linkCausedBy') }}:</strong> {{ t('helpCenter.ticketDetail.linkCausesDesc') }}</li>
      </HelpList>
    </HelpSection>

    <HelpSection :title="t('helpCenter.ticketDetail.activityTitle')">
      <p>{{ t('helpCenter.ticketDetail.activityText') }}</p>
      <HelpList>
        <li><strong>{{ t('helpCenter.ticketDetail.tabComments') }}:</strong> {{ t('helpCenter.ticketDetail.tabCommentsDesc') }}</li>
        <li><strong>{{ t('helpCenter.ticketDetail.tabChanges') }}:</strong> {{ t('helpCenter.ticketDetail.tabChangesDesc') }}</li>
        <li><strong>{{ t('helpCenter.ticketDetail.tabAll') }}:</strong> {{ t('helpCenter.ticketDetail.tabAllDesc') }}</li>
      </HelpList>
    </HelpSection>

    <HelpSection :title="t('helpCenter.ticketDetail.watchTitle')">
      <p>{{ t('helpCenter.ticketDetail.watchText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.ticketDetail.deleteTitle')">
      <p>{{ t('helpCenter.ticketDetail.deleteText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.ticketDetail.deleteModalTitle')">
      <NeutralContainer>
        <SubHeader class="mb-4">{{ t('common.delete') }}</SubHeader>
        <p class="mb-4">{{ t('helpCenter.ticketDetail.deleteConfirm') }}</p>
        <div class="flex justify-end gap-2">
          <DeleteButton>{{ t('common.delete') }}</DeleteButton>
        </div>
      </NeutralContainer>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.ticketDetail.tip') }}</HelpTip>
    <HelpTip>{{ t('helpCenter.ticketDetail.tip2') }}</HelpTip>
  </HelpArticle>
</template>
