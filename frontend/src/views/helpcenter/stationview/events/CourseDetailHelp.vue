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
import {RegistrationStatus, StationPermission} from '@/api/generated/schema'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.courseDetail.title')" :subtitle="t('helpCenter.courseDetail.subtitle')">
    <HelpSection :title="t('helpCenter.courseDetail.whatIs')">
      <p>{{ t('helpCenter.courseDetail.whatIsText') }}</p>
      <NeutralContainer class="space-y-2">
        <SubHeader>{{ t('helpCenter.courseDetail.practice') }} <MutedText tag="span" size="sm">({{ t('courses.setRule', {minimum: 2, count: 3}) }})</MutedText></SubHeader>
        <p class="text-sm">{{ t('helpCenter.courseDetail.day1') }}</p>
        <p class="text-sm">{{ t('helpCenter.courseDetail.day2') }}</p>
        <p class="text-sm"><span class="line-through text-(--text-muted)">{{ t('helpCenter.courseDetail.day3') }}</span> <ErrorBadge>{{ t('courses.partCalledOff') }}</ErrorBadge></p>
        <SubHeader>{{ t('helpCenter.courseDetail.test') }} <MutedText tag="span" size="sm">({{ t('courses.setAll') }})</MutedText></SubHeader>
        <p class="text-sm">{{ t('helpCenter.courseDetail.day4') }}</p>
      </NeutralContainer>
      <p>{{ t('helpCenter.courseDetail.calledOffText') }}</p>
    </HelpSection>
    <HelpSection :title="t('helpCenter.courseDetail.signUpTitle')">
      <p>{{ t('helpCenter.courseDetail.signUpText') }}</p>
      <NeutralContainer class="flex flex-wrap items-center justify-between gap-2">
        <div class="flex items-center gap-2">
          <span>{{ t('helpCenter.courseDetail.exampleMember') }}</span>
          <MutedText tag="span" size="sm">{{ t('courses.notSignedUp') }}</MutedText>
        </div>
        <PrimaryButton :icon="['fas', 'check']">{{ t('courses.signUp') }}</PrimaryButton>
      </NeutralContainer>
      <NeutralContainer class="space-y-2">
        <SubHeader>{{ t('courses.dialogTitle', {name: t('helpCenter.courseDetail.exampleMember')}) }}</SubHeader>
        <FieldLabel inline><CheckboxInput :model-value="true"/> {{ t('helpCenter.courseDetail.day1') }}</FieldLabel>
        <FieldLabel inline><CheckboxInput :model-value="false"/> {{ t('helpCenter.courseDetail.day2') }}</FieldLabel>
        <FieldLabel inline><CheckboxInput :model-value="true"/> {{ t('helpCenter.courseDetail.day4') }}</FieldLabel>
        <p class="text-sm text-error">{{ t('courses.setShort', {name: t('helpCenter.courseDetail.practice'), missing: 1}) }}</p>
      </NeutralContainer>
      <p>{{ t('helpCenter.courseDetail.dialogText') }}</p>
    </HelpSection>
    <HelpSection :title="t('helpCenter.courseDetail.changeTitle')">
      <p>{{ t('helpCenter.courseDetail.changeText') }}</p>
      <NeutralContainer class="flex flex-wrap items-center justify-between gap-2">
        <div class="flex items-center gap-2">
          <span>{{ t('helpCenter.courseDetail.exampleMember') }}</span>
          <RegistrationStatusBadge :status="RegistrationStatus.ACCEPTED"/>
          <MutedText tag="span" size="sm">{{ t('courses.chosen', {count: 3}) }}</MutedText>
        </div>
        <ButtonRow align="end">
          <SecondaryButton :icon="['fas', 'pen']">{{ t('courses.changeParts') }}</SecondaryButton>
          <SecondaryButton :icon="['fas', 'list-check']">{{ t('courses.changeAnswers') }}</SecondaryButton>
          <SecondaryButton :icon="['fas', 'xmark']">{{ t('courses.withdraw') }}</SecondaryButton>
        </ButtonRow>
      </NeutralContainer>
    </HelpSection>
    <HelpSection :title="t('helpCenter.courseDetail.manageTitle')">
      <HelpPermissionGuard :permissions="[StationPermission.EVENT_REGISTRATION]" :label="t('helpCenter.permissionLabel.eventRegistration')">
        <p>{{ t('helpCenter.courseDetail.manageText') }}</p>
        <NeutralContainer class="flex flex-wrap items-center justify-between gap-2">
          <div class="flex items-center gap-2">
            <span>{{ t('helpCenter.courseDetail.exampleOther') }}</span>
            <RegistrationStatusBadge :status="RegistrationStatus.PENDING"/>
            <ErrorBadge>{{ t('courses.short') }}</ErrorBadge>
          </div>
          <ButtonRow pair align="end">
            <SuccessButton compact>{{ t('courses.accept') }}</SuccessButton>
            <ErrorButton compact>{{ t('courses.deny') }}</ErrorButton>
          </ButtonRow>
        </NeutralContainer>
        <p>{{ t('helpCenter.courseDetail.partnerText') }}</p>
        <p>{{ t('helpCenter.courseDetail.attendanceText') }}</p>
      </HelpPermissionGuard>
      <HelpPermissionGuard :permissions="[StationPermission.EVENT_EDIT]" :label="t('helpCenter.permissionLabel.eventEdit')">
        <p>{{ t('helpCenter.courseDetail.editText') }}</p>
        <ButtonRow align="end">
          <SecondaryButton :icon="['fas', 'pen']">{{ t('courses.edit') }}</SecondaryButton>
          <SecondaryButton :icon="['fas', 'ban']">{{ t('courses.cancel') }}</SecondaryButton>
          <ErrorButton :icon="['fas', 'trash']">{{ t('courses.delete') }}</ErrorButton>
        </ButtonRow>
      </HelpPermissionGuard>
    </HelpSection>
    <HelpTip>{{ t('helpCenter.courseDetail.tip') }}</HelpTip>
  </HelpArticle>
</template>
