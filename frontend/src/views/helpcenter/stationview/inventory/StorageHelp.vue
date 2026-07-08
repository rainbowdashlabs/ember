/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import HelpTip from '@/components/helpcenter/HelpTip.vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.inventoryStorageHelp.title')" :subtitle="t('helpCenter.inventoryStorageHelp.subtitle')">
    <HelpSection :title="t('helpCenter.inventoryStorageHelp.purposeTitle')">
      <p>
        {{ t('helpCenter.inventoryStorageHelp.purposeText1') }}
      </p>
      <p>
        {{ t('helpCenter.inventoryStorageHelp.purposeText2') }}
      </p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryStorageHelp.createTitle')">
      <p>
        {{ t('helpCenter.inventoryStorageHelp.createText') }}
      </p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryStorageHelp.addItemsTitle')">
      <p>
        {{ t('helpCenter.inventoryStorageHelp.addItemsText') }}
      </p>
      <HelpTip>
        {{ t('helpCenter.inventoryStorageHelp.addItemsTip') }}
      </HelpTip>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryStorageHelp.scanTitle')">
      <p>
        {{ t('helpCenter.inventoryStorageHelp.scanText') }}
      </p>
      <HelpTip>
        {{ t('helpCenter.inventoryStorageHelp.scanTip') }}
      </HelpTip>
    </HelpSection>
  </HelpArticle>
</template>
