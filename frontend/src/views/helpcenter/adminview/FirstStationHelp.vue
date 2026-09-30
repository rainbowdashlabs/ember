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
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.adminFirstStation.title')" :subtitle="t('helpCenter.adminFirstStation.subtitle')">
    <HelpSection :title="t('helpCenter.adminFirstStation.whatIs')">
      <p>{{ t('helpCenter.adminFirstStation.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminFirstStation.howTo')">
      <p>{{ t('helpCenter.adminFirstStation.howToText') }}</p>
      <NeutralContainer class="space-y-3 mt-3">
        <FieldLabel>
          {{ t('firstStation.nameLabel') }}
          <TextInput :model-value="t('helpCenter.sample.stations.jfMusterstadt')" disabled/>
        </FieldLabel>
        <PrimaryButton :icon="['fas', 'building']" disabled>{{ t('firstStation.found') }}</PrimaryButton>
      </NeutralContainer>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminFirstStation.afterTitle')">
      <p>{{ t('helpCenter.adminFirstStation.afterText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.adminFirstStation.tip') }}</HelpTip>
  </HelpArticle>
</template>
