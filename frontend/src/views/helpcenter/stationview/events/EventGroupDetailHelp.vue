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
import HelpPermissionGuard from '@/components/helpcenter/HelpPermissionGuard.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SuccessButton from '@/components/button/SuccessButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import RegistrationStatusBadge from '@/views/stationview/events/eventshared/RegistrationStatusBadge.vue'
import EventGroupBand from '@/views/stationview/events/eventshared/EventGroupBand.vue'
import EventGroupHeading from '@/views/stationview/events/eventshared/EventGroupHeading.vue'
import ProbeVisitorList from '@/views/stationview/events/eventshared/ProbeVisitorList.vue'
import {RegistrationStatus, StationPermission, type WaitingListVisitor} from '@/api/generated/schema'

const {t} = useI18n()

const examplePart = computed(() => ({
  eventGroupId: 1,
  eventGroupName: t('helpCenter.eventGroups.exampleName'),
  setId: 1,
  setName: t('helpCenter.eventGroupDetail.practice'),
  number: 2,
  count: 4,
  color: '#8b5cf6',
  firstStart: '2026-12-12T08:00:00Z',
  lastEnd: '2026-12-20T15:00:00Z',
}))

const exampleVisitors = computed((): WaitingListVisitor[] => [{
  entryId: 1,
  listId: 1,
  firstname: t('helpCenter.eventGroupDetail.exampleVisitor'),
  lastname: t('helpCenter.eventGroupDetail.exampleVisitorLast'),
  listName: t('helpCenter.eventGroupDetail.exampleList'),
  arrivalTime: '09:30:00',
  date: '2026-12-12',
}])
</script>

<template>
  <HelpArticle :title="t('helpCenter.eventGroupDetail.title')" :subtitle="t('helpCenter.eventGroupDetail.subtitle')">
    <HelpSection :title="t('helpCenter.eventGroups.whatIs')">
      <p>{{ t('helpCenter.eventGroups.whatIsText') }}</p>
      <p>{{ t('helpCenter.eventGroups.whatIsExample') }}</p>
    </HelpSection>
    <HelpSection :title="t('helpCenter.eventGroups.howTo')">
      <p>{{ t('helpCenter.eventGroups.howToText') }}</p>
      <NeutralContainer class="relative pl-5 pt-9">
        <EventGroupBand :event-group="examplePart"/>
        <EventGroupHeading :event-group="examplePart"/>
        <span class="font-medium">{{ t('helpCenter.eventGroupDetail.day2') }}</span>
      </NeutralContainer>
    </HelpSection>
    <HelpSection :title="t('helpCenter.eventGroupDetail.whatIs')">
      <p>{{ t('helpCenter.eventGroupDetail.whatIsText') }}</p>
      <NeutralContainer class="space-y-2">
        <SubHeader>{{ t('helpCenter.eventGroupDetail.practice') }} <MutedText tag="span" size="sm">({{ t('eventGroups.setRule', {minimum: 2, count: 3}) }})</MutedText></SubHeader>
        <p class="text-sm">{{ t('helpCenter.eventGroupDetail.day1') }}</p>
        <p class="text-sm">{{ t('helpCenter.eventGroupDetail.day2') }}</p>
        <p class="text-sm"><span class="line-through text-(--text-muted)">{{ t('helpCenter.eventGroupDetail.day3') }}</span> <ErrorBadge>{{ t('eventGroups.partCalledOff') }}</ErrorBadge></p>
        <SubHeader>{{ t('helpCenter.eventGroupDetail.test') }} <MutedText tag="span" size="sm">({{ t('eventGroups.setAll') }})</MutedText></SubHeader>
        <p class="text-sm">{{ t('helpCenter.eventGroupDetail.day4') }}</p>
      </NeutralContainer>
      <p>{{ t('helpCenter.eventGroupDetail.calledOffText') }}</p>
      <p>{{ t('helpCenter.eventGroupDetail.minimumText') }}</p>
      <MutedText tag="p" size="sm">{{ t('eventGroups.minimumRule', {minimum: 6, days: 7}) }}</MutedText>
    </HelpSection>
    <HelpSection :title="t('helpCenter.eventGroupDetail.signUpTitle')">
      <p>{{ t('helpCenter.eventGroupDetail.signUpText') }}</p>
      <NeutralContainer class="flex flex-wrap items-center justify-between gap-2">
        <div class="flex items-center gap-2">
          <span>{{ t('helpCenter.eventGroupDetail.exampleMember') }}</span>
          <MutedText tag="span" size="sm">{{ t('eventGroups.notSignedUp') }}</MutedText>
        </div>
        <PrimaryButton :icon="['fas', 'check']">{{ t('eventGroups.signUp') }}</PrimaryButton>
      </NeutralContainer>
      <NeutralContainer class="space-y-2">
        <SubHeader>{{ t('eventGroups.dialogTitle', {name: t('helpCenter.eventGroupDetail.exampleMember')}) }}</SubHeader>
        <FieldLabel inline><CheckboxInput :model-value="true"/> {{ t('helpCenter.eventGroupDetail.day1') }}</FieldLabel>
        <FieldLabel inline><CheckboxInput :model-value="false"/> {{ t('helpCenter.eventGroupDetail.day2') }}</FieldLabel>
        <FieldLabel inline><CheckboxInput :model-value="true"/> {{ t('helpCenter.eventGroupDetail.day4') }}</FieldLabel>
        <p class="text-sm text-error">{{ t('eventGroups.setShort', {name: t('helpCenter.eventGroupDetail.practice'), missing: 1}) }}</p>
      </NeutralContainer>
      <p>{{ t('helpCenter.eventGroupDetail.dialogText') }}</p>
    </HelpSection>
    <HelpSection :title="t('helpCenter.eventGroupDetail.memberFieldsTitle')">
      <p>{{ t('helpCenter.eventGroupDetail.memberFieldsText') }}</p>
    </HelpSection>
    <HelpSection :title="t('helpCenter.eventGroupDetail.changeTitle')">
      <p>{{ t('helpCenter.eventGroupDetail.changeText') }}</p>
      <NeutralContainer class="flex flex-wrap items-center justify-between gap-2">
        <div class="flex items-center gap-2">
          <span>{{ t('helpCenter.eventGroupDetail.exampleMember') }}</span>
          <RegistrationStatusBadge :status="RegistrationStatus.ACCEPTED"/>
          <MutedText tag="span" size="sm">{{ t('eventGroups.chosen', {count: 3}) }}</MutedText>
        </div>
        <ButtonRow align="end">
          <SecondaryButton :icon="['fas', 'pen']">{{ t('eventGroups.changeParts') }}</SecondaryButton>
          <SecondaryButton :icon="['fas', 'list-check']">{{ t('eventGroups.changeAnswers') }}</SecondaryButton>
          <SecondaryButton :icon="['fas', 'xmark']">{{ t('eventGroups.withdraw') }}</SecondaryButton>
        </ButtonRow>
      </NeutralContainer>
    </HelpSection>
    <HelpSection :title="t('helpCenter.eventGroupDetail.materialsTitle')">
      <p>{{ t('helpCenter.eventGroupDetail.materialsText') }}</p>
      <p>{{ t('helpCenter.eventGroupDetail.signingText') }}</p>
      <p>{{ t('helpCenter.eventGroupDetail.partSigningText') }}</p>
    </HelpSection>
    <HelpSection :title="t('helpCenter.eventGroupDetail.manageTitle')">
      <HelpPermissionGuard :permissions="[StationPermission.EVENT_REGISTRATION]" :label="t('helpCenter.permissionLabel.eventRegistration')">
        <p>{{ t('helpCenter.eventGroupDetail.manageText') }}</p>
        <NeutralContainer class="flex flex-wrap items-center justify-between gap-2">
          <div class="flex items-center gap-2">
            <span>{{ t('helpCenter.eventGroupDetail.exampleOther') }}</span>
            <RegistrationStatusBadge :status="RegistrationStatus.PENDING"/>
            <ErrorBadge>{{ t('eventGroups.short') }}</ErrorBadge>
          </div>
          <ButtonRow pair align="end">
            <SuccessButton compact>{{ t('eventGroups.accept') }}</SuccessButton>
            <ErrorButton compact>{{ t('eventGroups.deny') }}</ErrorButton>
          </ButtonRow>
        </NeutralContainer>
        <p>{{ t('helpCenter.eventGroupDetail.exportText') }}</p>
        <SecondaryButton :icon="['fas', 'file-export']">{{ t('eventGroups.exportSignUps') }}</SecondaryButton>
        <p>{{ t('helpCenter.eventGroupDetail.partnerText') }}</p>
        <p>{{ t('helpCenter.eventGroupDetail.attendanceText') }}</p>
        <p>{{ t('helpCenter.eventGroupDetail.signersText') }}</p>
      </HelpPermissionGuard>
      <HelpPermissionGuard :permissions="[StationPermission.EVENT_EDIT]" :label="t('helpCenter.permissionLabel.eventEdit')">
        <p>{{ t('helpCenter.eventGroupDetail.editText') }}</p>
        <ButtonRow align="end">
          <SecondaryButton :icon="['fas', 'pen']">{{ t('eventGroups.edit') }}</SecondaryButton>
          <SecondaryButton :icon="['fas', 'ban']">{{ t('eventGroups.cancel') }}</SecondaryButton>
          <ErrorButton :icon="['fas', 'trash']">{{ t('eventGroups.delete') }}</ErrorButton>
        </ButtonRow>
      </HelpPermissionGuard>
    </HelpSection>
    <HelpPermissionGuard :permissions="[StationPermission.WAITLIST_READ]" :label="t('helpCenter.permissionLabel.waitlistRead')">
      <HelpSection :title="t('helpCenter.eventGroupDetail.probeVisitorsTitle')">
        <p>{{ t('helpCenter.eventGroupDetail.probeVisitorsText') }}</p>
        <NeutralContainer class="space-y-2">
          <SubHeader>{{ t('eventDetail.probeVisitors') }}</SubHeader>
          <MutedText tag="p" size="sm">{{ t('eventGroups.probeVisitorsHint') }}</MutedText>
          <p class="text-sm font-medium">{{ t('helpCenter.eventGroupDetail.day1') }}</p>
          <ProbeVisitorList :visitors="exampleVisitors"/>
        </NeutralContainer>
      </HelpSection>
    </HelpPermissionGuard>
    <HelpPermissionGuard :permissions="[StationPermission.EVENT_EDIT]" :label="t('helpCenter.permissionLabel.eventEdit')">
      <HelpSection :title="t('helpCenter.eventGroupDetail.notesTitle')">
        <p>{{ t('helpCenter.eventGroupDetail.notesText') }}</p>
      </HelpSection>
    </HelpPermissionGuard>
    <HelpSection :title="t('helpCenter.eventGroupDetail.commentsTitle')">
      <p>{{ t('helpCenter.eventGroupDetail.commentsText') }}</p>
      <p>{{ t('helpCenter.eventGroupDetail.commentsPartnerText') }}</p>
    </HelpSection>
    <HelpTip>{{ t('helpCenter.eventGroupDetail.tip') }}</HelpTip>
  </HelpArticle>
</template>
