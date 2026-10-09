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
import StationEditExample from './stationedithelp/StationEditExample.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import ToggleSetting from '@/components/input/toggle/ToggleSetting.vue'
import LabelledField from '@/components/input/LabelledField.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.adminStationEdit.title')" :subtitle="t('helpCenter.adminStationEdit.subtitle')">
    <HelpSection :title="t('helpCenter.adminStationEdit.whatShown')">
      <p>{{ t('helpCenter.adminStationEdit.whatShownText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminStationEdit.createModeTitle')">
      <p>{{ t('helpCenter.adminStationEdit.createModeText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminStationEdit.nameTitle')">
      <p>{{ t('helpCenter.adminStationEdit.nameText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminStationEdit.managerTitle')">
      <p>{{ t('helpCenter.adminStationEdit.managerText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminStationEdit.exampleTitle')">
      <StationEditExample/>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminStationEdit.instanceMailTitle')">
      <p>{{ t('helpCenter.adminStationEdit.instanceMailText') }}</p>
      <NeutralContainer class="space-y-4">
        <SectionHeader>{{ t('instanceMail.grant.title') }}</SectionHeader>
        <ToggleSetting :model-value="true" :label="t('instanceMail.grant.toggle')" disabled/>
        <LabelledField :label="t('instanceMail.grant.dailyLimit')" :help="t('instanceMail.grant.dailyLimitHint')" hint>
          <NumberInput :model-value="50" disabled/>
        </LabelledField>
        <MutedText tag="p" size="sm">{{ t('instanceMail.grant.sentToday', {count: 12}) }}</MutedText>
      </NeutralContainer>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.adminStationEdit.tip') }}</HelpTip>
  </HelpArticle>
</template>
