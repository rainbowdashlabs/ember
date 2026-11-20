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
import MutedText from '@/components/typography/MutedText.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import RegistrationStatusBadge from '@/views/stationview/events/eventshared/RegistrationStatusBadge.vue'
import {RegistrationStatus, StationPermission} from '@/api/generated/schema'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.federatedEventGroup.title')" :subtitle="t('helpCenter.federatedEventGroup.subtitle')">
    <HelpSection :title="t('helpCenter.federatedEventGroup.whatIs')">
      <p>{{ t('helpCenter.federatedEventGroup.whatIsText') }}</p>
    </HelpSection>
    <HelpSection :title="t('helpCenter.federatedEventGroup.signUpTitle')">
      <p>{{ t('helpCenter.federatedEventGroup.signUpText') }}</p>
      <p>{{ t('helpCenter.federatedEventGroup.partsText') }}</p>
      <p>{{ t('helpCenter.federatedEventGroup.signingText') }}</p>
    </HelpSection>
    <HelpSection :title="t('helpCenter.federatedEventGroup.placesTitle')">
      <p>{{ t('helpCenter.federatedEventGroup.placesText') }}</p>
      <HelpPermissionGuard :permissions="[StationPermission.EVENT_REGISTRATION]" :label="t('helpCenter.permissionLabel.eventRegistration')">
        <p>{{ t('helpCenter.federatedEventGroup.confirmText') }}</p>
        <NeutralContainer class="space-y-3">
          <MutedText tag="p" size="sm">{{ t('helpCenter.federatedEventGroup.examplePlaces') }}</MutedText>
          <div class="flex flex-wrap items-center justify-between gap-2">
            <div class="flex flex-wrap items-center gap-2">
              <span class="font-medium">{{ t('helpCenter.federatedEventGroup.exampleMember') }}</span>
              <RegistrationStatusBadge :status="RegistrationStatus.PENDING"/>
            </div>
            <ButtonRow align="end">
              <PrimaryButton :icon="['fas', 'check']">{{ t('common.confirm') }}</PrimaryButton>
              <SecondaryButton :icon="['fas', 'xmark']">{{ t('eventGroups.withdraw') }}</SecondaryButton>
            </ButtonRow>
          </div>
        </NeutralContainer>
      </HelpPermissionGuard>
    </HelpSection>
    <HelpSection :title="t('helpCenter.federatedEventGroup.toldTitle')">
      <p>{{ t('helpCenter.federatedEventGroup.toldText') }}</p>
    </HelpSection>
    <HelpSection :title="t('helpCenter.federatedEventGroup.commentsTitle')">
      <p>{{ t('helpCenter.federatedEventGroup.commentsText') }}</p>
    </HelpSection>
    <HelpTip>{{ t('helpCenter.federatedEventGroup.tip') }}</HelpTip>
  </HelpArticle>
</template>
