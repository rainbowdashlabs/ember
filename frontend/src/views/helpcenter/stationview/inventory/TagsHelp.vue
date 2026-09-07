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
import TagsDummy from './tagshelp/TagsDummy.vue'
import {StationPermission} from '@/api/types'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.inventoryTags.title')" :subtitle="t('helpCenter.inventoryTags.subtitle')">
    <HelpSection :title="t('helpCenter.inventoryTags.whatIsTitle')">
      <p>{{ t('helpCenter.inventoryTags.whatIsText') }}</p>
      <p>{{ t('helpCenter.inventoryTags.againstArtText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryTags.pickedTitle')">
      <p>{{ t('helpCenter.inventoryTags.pickedText') }}</p>
      <TagsDummy/>
    </HelpSection>

    <HelpPermissionGuard
        :permissions="[StationPermission.INVENTORY_EDIT]"
        :label="t('helpCenter.permissionLabel.inventoryEdit')"
    >
      <HelpSection :title="t('helpCenter.inventoryTags.manageTitle')">
        <p>{{ t('helpCenter.inventoryTags.manageText') }}</p>
      </HelpSection>
    </HelpPermissionGuard>

    <HelpSection :title="t('helpCenter.inventoryTags.filterTitle')">
      <p>{{ t('helpCenter.inventoryTags.filterText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryTags.sameWordTitle')">
      <p>{{ t('helpCenter.inventoryTags.sameWordText') }}</p>
      <p>{{ t('helpCenter.inventoryTags.clusterText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.inventoryTags.tip') }}</HelpTip>
  </HelpArticle>
</template>
