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

    <HelpTip>{{ t('helpCenter.adminStationEdit.tip') }}</HelpTip>
  </HelpArticle>
</template>
