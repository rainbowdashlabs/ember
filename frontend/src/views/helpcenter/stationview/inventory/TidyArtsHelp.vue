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
import TidyNamesTable from '@/views/stationview/inventory/tidyartsview/TidyNamesTable.vue'
import TidyArtTarget from '@/views/stationview/inventory/tidyartsview/TidyArtTarget.vue'
import TidyActions from '@/views/stationview/inventory/tidyartsview/TidyActions.vue'
import {names, selected, selectedPieces, targetName} from './tidyartshelp/fixtures'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.inventoryTidy.title')" :subtitle="t('helpCenter.inventoryTidy.subtitle')">
    <HelpSection :title="t('helpCenter.inventoryTidy.whatIsArtTitle')">
      <p>{{ t('helpCenter.inventoryTidy.whatIsArtText') }}</p>
      <p>{{ t('helpCenter.inventoryTidy.threeWordsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryTidy.whereTitle')">
      <p>{{ t('helpCenter.inventoryTidy.whereText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryTidy.screenTitle')">
      <p>{{ t('helpCenter.inventoryTidy.screenText') }}</p>
      <div class="space-y-3">
        <TidyNamesTable :names="names" :selected="selected"/>
        <TidyArtTarget :art-id="null" :name="targetName" :arts="[]"/>
        <TidyActions :pieces="selectedPieces" :name="targetName" can-tidy :merging="false" :assigning="false"/>
      </div>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryTidy.twoButtonsTitle')">
      <p>{{ t('helpCenter.inventoryTidy.twoButtonsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryTidy.fieldsTitle')">
      <p>{{ t('helpCenter.inventoryTidy.fieldsText') }}</p>
      <p>{{ t('helpCenter.inventoryTidy.fieldsWhereText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.inventoryTidy.tip') }}</HelpTip>
  </HelpArticle>
</template>
