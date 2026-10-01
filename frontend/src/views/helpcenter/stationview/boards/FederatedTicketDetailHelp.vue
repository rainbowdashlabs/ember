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
import TicketRightColumn from '@/views/stationview/boards/ticketdetailview/TicketRightColumn.vue'
import {ticketDetailFixtures} from './ticketdetailhelp/fixtures'

const {t} = useI18n()
const fixture = ticketDetailFixtures(t)
</script>

<template>
  <HelpArticle :title="t('helpCenter.federatedTicketDetail.title')"
               :subtitle="t('helpCenter.federatedTicketDetail.subtitle')">
    <HelpSection :title="t('helpCenter.federatedTicketDetail.whatIs')">
      <p>{{ t('helpCenter.federatedTicketDetail.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.federatedTicketDetail.howTo')">
      <p>{{ t('helpCenter.federatedTicketDetail.howToStep1') }}</p>
      <p>{{ t('helpCenter.federatedTicketDetail.howToStep2') }}</p>
      <p>{{ t('helpCenter.federatedTicketDetail.howToStep3') }}</p>
      <TicketRightColumn
          :ticket="fixture.ticket" :lanes="fixture.lanes" :members="fixture.members" :assignable-members="fixture.members"
          :all-labels="fixture.allLabels" :ticket-labels="fixture.ticketLabels" :board-fields="fixture.boardFields"
          :priority-options="fixture.priorityChoices" :can-edit="false" :failure="null" :priority="fixture.ticket.priority"
          :assigned-member-id="fixture.assignedMemberId" :due-date="fixture.ticket.dueDate ?? ''" :field-values="fixture.fieldValues"
      />
    </HelpSection>

    <HelpSection :title="t('helpCenter.federatedTicketDetail.readOnlyTitle')">
      <p>{{ t('helpCenter.federatedTicketDetail.readOnlyText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.federatedTicketDetail.detailTitle')">
      <p>{{ t('helpCenter.federatedTicketDetail.detailText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.federatedTicketDetail.tip') }}</HelpTip>
  </HelpArticle>
</template>
