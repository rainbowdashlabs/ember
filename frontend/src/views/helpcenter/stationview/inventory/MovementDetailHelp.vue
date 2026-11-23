/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import CallOffChoice from '@/views/stationview/inventory/movementview/CallOffChoice.vue'
import {OldPieceWhereabouts} from '@/api/generated/schema'
import HelpTip from '@/components/helpcenter/HelpTip.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.movementDetail.title')" :subtitle="t('helpCenter.movementDetail.subtitle')">
    <HelpSection :title="t('helpCenter.movementDetail.whatIs')">
      <p>{{ t('helpCenter.movementDetail.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.movementDetail.steps')">
      <p>{{ t('helpCenter.movementDetail.stepsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.movementDetail.acks')">
      <p>{{ t('helpCenter.movementDetail.acksText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.movementDetail.arrival')">
      <p>{{ t('helpCenter.movementDetail.arrivalText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.movementDetail.order')">
      <p>{{ t('helpCenter.movementDetail.orderText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.movementDetail.endings')">
      <p>{{ t('helpCenter.movementDetail.endingsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.movementDetail.callOff')">
      <p>{{ t('helpCenter.movementDetail.callOffAtStationText') }}</p>
      <CallOffChoice :old-piece="OldPieceWhereabouts.AT_STATION" :give-back="true"/>
      <p>{{ t('helpCenter.movementDetail.callOffAwayText') }}</p>
      <CallOffChoice :old-piece="OldPieceWhereabouts.AWAY" :plan-way-back="true"/>
      <p>{{ t('helpCenter.movementDetail.callOffMemberText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.movementDetail.rechain')">
      <p>{{ t('helpCenter.movementDetail.rechainText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.movementDetail.lossReport')">
      <p>{{ t('helpCenter.movementDetail.lossReportText') }}</p>
      <p>{{ t('helpCenter.movementDetail.lossReportEndingsText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.movementDetail.tip') }}</HelpTip>
  </HelpArticle>
</template>
