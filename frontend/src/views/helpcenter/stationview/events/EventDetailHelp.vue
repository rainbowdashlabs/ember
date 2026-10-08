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
import HelpPermissionGuard from '@/components/helpcenter/HelpPermissionGuard.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import ActionsMenu from '@/components/button/ActionsMenu.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import DropdownMenuItem from '@/components/button/DropdownMenuItem.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import DetailLabel from '@/components/typography/DetailLabel.vue'
import EventCancellationBanner from '@/views/stationview/events/eventdetailview/EventCancellationBanner.vue'
import {CancellationCause, StationPermission, type CancellationNotice} from '@/api/generated/schema'

const {t} = useI18n()

const sampleCancellation: CancellationNotice = {
  date: '2026-05-25',
  cause: CancellationCause.THRESHOLD,
  reason: null,
  cancelledAt: null,
}
</script>

<template>
  <HelpArticle :title="t('helpCenter.eventDetail.title')" :subtitle="t('helpCenter.eventDetail.subtitle')">
    <HelpSection :title="t('helpCenter.eventDetail.whatIs')">
      <p>{{ t('helpCenter.eventDetail.whatIsText') }}</p>
      <p>{{ t('helpCenter.eventDetail.memberAccess') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.eventDetail.answeringTitle')">
      <p>{{ t('helpCenter.eventDetail.answeringText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.eventDetail.changingTitle')">
      <p>{{ t('helpCenter.eventDetail.changingText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.eventDetail.laterQuestionTitle')">
      <p>{{ t('helpCenter.eventDetail.laterQuestionText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.eventDetail.reminderTitle')">
      <p>{{ t('helpCenter.eventDetail.reminderText') }}</p>
    </HelpSection>

    <HelpSection :title="t('events.general')">
      <div class="flex items-center justify-between flex-wrap gap-3">
        <div class="flex items-center gap-3">
          <SectionHeader>{{ t('helpCenter.sample.events.competitionPrep') }}</SectionHeader>
          <SecondaryBadge>{{ t('events.typeOneTime') }}</SecondaryBadge>
        </div>
        <ButtonRow>
          <SecondaryButton><font-awesome-icon :icon="['fas', 'arrow-left']" class="mr-1"/>{{ t('common.back') }}</SecondaryButton>
          <HelpPermissionGuard :permissions="[StationPermission.EVENT_EDIT]" :label="t('helpCenter.permissionLabel.eventEdit')">
            <PrimaryButton><font-awesome-icon :icon="['fas', 'pen']" class="mr-1"/>{{ t('events.editEvent') }}</PrimaryButton>
          </HelpPermissionGuard>
          <ActionsMenu :label="t('common.actions')" test-id="help-event-actions">
            <DropdownMenuItem :icon="['fas', 'bullhorn']">{{ t('events.announceAsNews') }}</DropdownMenuItem>
            <DropdownMenuItem :icon="['fas', 'ban']" destructive>{{ t('events.cancelDate') }}</DropdownMenuItem>
            <DropdownMenuItem :icon="['fas', 'calendar-xmark']" destructive>{{ t('events.cancelSeries') }}</DropdownMenuItem>
          </ActionsMenu>
        </ButtonRow>
      </div>

      <NeutralContainer class="space-y-3 mt-3">
        <SubHeader>{{ t('events.general') }}</SubHeader>
        <div class="grid gap-4 sm:grid-cols-2">
          <div class="sm:col-span-2">
            <DetailLabel>{{ t('events.description') }}</DetailLabel>
            <p class="text-sm mt-1">{{ t('helpCenter.sample.events.competitionPrepText') }}</p>
          </div>
          <div>
            <DetailLabel>{{ t('events.category') }}</DetailLabel>
            <p class="text-sm">{{ t('helpCenter.sample.events.competition') }}</p>
          </div>
          <div>
            <DetailLabel>{{ t('events.startTime') }}</DetailLabel>
            <p class="text-sm">{{ t('helpCenter.sample.events.mondayStart') }}</p>
          </div>
          <div>
            <DetailLabel>{{ t('events.endTime') }}</DetailLabel>
            <p class="text-sm">{{ t('helpCenter.sample.events.mondayEnd') }}</p>
          </div>
        </div>
      </NeutralContainer>
    </HelpSection>

    <HelpSection :title="t('helpCenter.eventDetail.registrationTitle')">
      <NeutralContainer class="space-y-3">
        <div class="flex items-center gap-2 flex-wrap">
          <span class="text-sm font-medium">{{ t('helpCenter.eventDetail.yourStatus') }}:</span>
          <InfoBadge>{{ t('eventsUpcoming.statusPending') }}</InfoBadge>
        </div>
        <ButtonRow pair>
          <PrimaryButton :icon="['fas', 'check']" disabled>
            {{ t('eventsUpcoming.register') }}
          </PrimaryButton>
          <ErrorButton :icon="['fas', 'xmark']" disabled>
            {{ t('eventsUpcoming.decline') }}
          </ErrorButton>
        </ButtonRow>
      </NeutralContainer>
    </HelpSection>

    <HelpPermissionGuard :permissions="[StationPermission.EVENT_REGISTRATION]" :label="t('helpCenter.permissionLabel.eventManage')">
      <HelpSection :title="t('eventDetail.registrations')">
        <NeutralContainer class="space-y-2">
          <SubHeader>{{ t('eventDetail.registrations') }}</SubHeader>
          <div class="flex flex-wrap gap-2">
            <SuccessBadge>3 {{ t('eventsUpcoming.accepted') }}</SuccessBadge>
            <InfoBadge>1 {{ t('eventsUpcoming.pendingCount') }}</InfoBadge>
          </div>
          <div class="space-y-1 text-sm">
            <div class="flex items-center gap-2">
              <SuccessBadge>{{ t('eventsUpcoming.statusAccepted') }}</SuccessBadge>
              <span>{{ t('helpCenter.sample.people.maxMustermann') }}</span>
            </div>
            <div class="flex items-center gap-2">
              <SuccessBadge>{{ t('eventsUpcoming.statusAccepted') }}</SuccessBadge>
              <span>{{ t('helpCenter.sample.people.lisaSchmidt') }}</span>
            </div>
            <div class="flex items-center gap-2">
              <InfoBadge>{{ t('eventsUpcoming.statusPending') }}</InfoBadge>
              <span>{{ t('helpCenter.sample.people.tomMueller') }}</span>
            </div>
          </div>
        </NeutralContainer>
      </HelpSection>
    </HelpPermissionGuard>

    <HelpPermissionGuard
        :permissions="[StationPermission.CHECKLIST_MANAGE, StationPermission.POLL_CREATE]"
        :label="t('helpCenter.permissionLabel.checklistManage')">
      <HelpSection :title="t('helpCenter.eventDetail.signupListsTitle')">
        <p>{{ t('helpCenter.eventDetail.signupListsText') }}</p>
        <p>{{ t('helpCenter.eventDetail.signupListsLimitsText') }}</p>
        <p>{{ t('helpCenter.eventDetail.signupListsSurveyText') }}</p>
      </HelpSection>
    </HelpPermissionGuard>

    <HelpSection :title="t('helpCenter.eventDetail.badgesTitle')">
      <p>{{ t('helpCenter.eventDetail.badgesText') }}</p>
      <div class="flex flex-wrap gap-2 mt-3">
        <SuccessBadge>{{ t('eventsUpcoming.statusAccepted') }}</SuccessBadge>
        <InfoBadge>{{ t('eventsUpcoming.statusPending') }}</InfoBadge>
        <ErrorBadge>{{ t('eventsUpcoming.statusDenied') }}</ErrorBadge>
        <ErrorBadge>{{ t('eventsUpcoming.statusDeclined') }}</ErrorBadge>
      </div>
    </HelpSection>

    <HelpPermissionGuard :permissions="[StationPermission.NEWS_EDIT]" :label="t('helpCenter.permissionLabel.newsEdit')">
      <HelpSection :title="t('helpCenter.eventDetail.announceTitle')">
        <p>{{ t('helpCenter.eventDetail.announceText') }}</p>
        <p>{{ t('helpCenter.eventDetail.announceDateText') }}</p>
        <p>{{ t('helpCenter.eventDetail.announceAudienceText') }}</p>
        <p>{{ t('helpCenter.eventDetail.announceSnapshotText') }}</p>
      </HelpSection>
    </HelpPermissionGuard>

    <HelpPermissionGuard :permissions="[StationPermission.EVENT_EDIT]" :label="t('helpCenter.permissionLabel.eventEdit')">
      <HelpSection :title="t('helpCenter.eventDetail.cancelTitle')">
        <p>{{ t('helpCenter.eventDetail.cancelText') }}</p>
        <p>{{ t('helpCenter.eventDetail.cancelSeriesText') }}</p>
        <p>{{ t('helpCenter.eventDetail.restoreText') }}</p>
        <p>{{ t('helpCenter.eventDetail.cancelAutomaticText') }}</p>
        <EventCancellationBanner class="mt-3" :cancellation="sampleCancellation" :series-cancelled="false"/>
      </HelpSection>
    </HelpPermissionGuard>

    <HelpSection :title="t('helpCenter.eventDetail.nextOccurrenceTitle')">
      <p>{{ t('helpCenter.eventDetail.nextOccurrenceText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.eventDetail.attachmentsTitle')">
      <p>{{ t('helpCenter.eventDetail.attachmentsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.eventDetail.documentsToBringTitle')">
      <p>{{ t('helpCenter.eventDetail.documentsToBringText') }}</p>
      <p>{{ t('helpCenter.eventDetail.documentsToBringDownloadText') }}</p>
      <p>{{ t('helpCenter.eventDetail.documentsToBringScanText') }}</p>
      <p>{{ t('helpCenter.eventDetail.documentsToBringScanReviewText') }}</p>
      <p>{{ t('helpCenter.eventDetail.documentsToBringSigningText') }}</p>
      <p>{{ t('helpCenter.eventDetail.documentsToBringSigningStatusText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.eventDetail.commentsTitle')">
      <p>{{ t('helpCenter.eventDetail.commentsText') }}</p>
    </HelpSection>

    <HelpPermissionGuard :permissions="[StationPermission.EVENT_EDIT]" :label="t('helpCenter.permissionLabel.eventEdit')">
      <HelpSection :title="t('helpCenter.eventDetail.notesTitle')">
        <p>{{ t('helpCenter.eventDetail.notesText') }}</p>
      </HelpSection>
    </HelpPermissionGuard>

    <HelpSection :title="t('helpCenter.eventDetail.federationTitle')">
      <p>{{ t('helpCenter.eventDetail.federationText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.eventDetail.absentTitle')">
      <p>{{ t('helpCenter.eventDetail.absentText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.eventDetail.templateTitle')">
      <p>{{ t('helpCenter.eventDetail.templateText') }}</p>
      <p>{{ t('helpCenter.eventDetail.attendanceMenuText') }}</p>
      <p>{{ t('helpCenter.eventDetail.attendanceDayText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.eventDetail.tip') }}</HelpTip>
  </HelpArticle>
</template>
