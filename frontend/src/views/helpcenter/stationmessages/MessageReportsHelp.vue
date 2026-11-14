/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import HelpTip from '@/components/helpcenter/HelpTip.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {ConversationMessageResponse, ReportedMessageResponse} from '@/api/generated/schema'
import ReportCard from '@/views/stationview/messages/messagereportsview/ReportCard.vue'

const {t} = useI18n()

function line(id: number, firstName: string, lastName: string, content: string, editedAt: string | null = null): ConversationMessageResponse {
  return {
    id, author: {uid: `00000000-0000-0000-0000-0000000000f${id}`, firstName, lastName},
    content, replyTo: null, systemKind: null, subject: null, createdAt: new Date().toISOString(), editedAt,
    deleted: false, reactions: [], removedByStation: false, removedBy: null,
  }
}

const report = computed<ReportedMessageResponse>(() => ({
  id: 1, conversationUid: '00000000-0000-0000-0000-0000000000e1', chatName: t('helpCenter.messageReports.example.chat'),
  reporter: {uid: '00000000-0000-0000-0000-0000000000f9', firstName: t('helpCenter.messageReports.example.reporter'), lastName: t('helpCenter.messageReports.example.reporterLast')},
  reason: t('helpCenter.messageReports.example.reason'), reportedText: t('helpCenter.messageReports.example.reported'),
  reportedAt: new Date().toISOString(), messageId: 2,
  context: [
    line(1, t('helpCenter.messageReports.example.before'), t('helpCenter.messageReports.example.beforeLast'), t('helpCenter.messageReports.example.beforeText')),
    line(2, t('helpCenter.messageReports.example.author'), t('helpCenter.messageReports.example.authorLast'), t('helpCenter.messageReports.example.changed'), new Date().toISOString()),
    line(3, t('helpCenter.messageReports.example.before'), t('helpCenter.messageReports.example.beforeLast'), t('helpCenter.messageReports.example.afterText')),
  ],
}))

function nothing(): void {
  return undefined
}
</script>

<template>
  <HelpArticle :title="t('helpCenter.messageReports.title')" :subtitle="t('helpCenter.messageReports.subtitle')">
    <HelpSection :title="t('helpCenter.messageReports.whatIs')">
      <p>{{ t('helpCenter.messageReports.whatIsText') }}</p>
      <p>{{ t('helpCenter.messageReports.notifyText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.messageReports.howTo')">
      <p>{{ t('helpCenter.messageReports.howToText') }}</p>
      <div inert>
        <ReportCard :report="report" :on-handled="nothing"/>
      </div>
    </HelpSection>

    <HelpSection :title="t('helpCenter.messageReports.decideTitle')">
      <p>{{ t('helpCenter.messageReports.removeText') }}</p>
      <NeutralContainer>
        <MutedText tag="p">{{ t('messageReports.removeConfirm') }}</MutedText>
      </NeutralContainer>
      <p>{{ t('helpCenter.messageReports.dismissText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.messageReports.emptyTitle')">
      <EmptyState :message="t('messageReports.empty')"/>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.messageReports.tip') }}</HelpTip>
  </HelpArticle>
</template>
