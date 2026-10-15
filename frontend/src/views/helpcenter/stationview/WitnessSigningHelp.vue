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
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import WitnessHandOverStep from '@/views/stationview/signing/witnessview/WitnessHandOverStep.vue'

/**
 * Signatures collected in person: who may collect them, the four steps, what the person sees while they
 * hold the device, where the document goes and what the evidence says.
 */
const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.witnessSigning.title')" :subtitle="t('helpCenter.witnessSigning.subtitle')">
    <HelpSection :title="t('helpCenter.witnessSigning.whatIs')">
      <p>{{ t('helpCenter.witnessSigning.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.witnessSigning.who')">
      <p>{{ t('helpCenter.witnessSigning.whoText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.witnessSigning.steps')">
      <p>{{ t('helpCenter.witnessSigning.stepPerson') }}</p>
      <p>{{ t('helpCenter.witnessSigning.stepDocument') }}</p>
      <p>{{ t('helpCenter.witnessSigning.stepHandOver') }}</p>
      <p>{{ t('helpCenter.witnessSigning.stepTakeBack') }}</p>
      <p>{{ t('helpCenter.witnessSigning.handedOver') }}</p>
    </HelpSection>

    <NeutralContainer>
      <WitnessHandOverStep :name="t('helpCenter.signing.exampleChild')" :position="4" :total="8"/>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.witnessSigning.where')">
      <p>{{ t('helpCenter.witnessSigning.whereText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.witnessSigning.evidence')">
      <p>{{ t('helpCenter.witnessSigning.evidenceText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.witnessSigning.fromTile')">
      <p>{{ t('helpCenter.witnessSigning.fromTileText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.witnessSigning.tip') }}</HelpTip>
  </HelpArticle>
</template>
