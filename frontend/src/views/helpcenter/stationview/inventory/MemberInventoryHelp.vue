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
import MemberInventoryHeader from '@/views/stationview/inventory/memberinventoryview/MemberInventoryHeader.vue'
import MemberInventoryGroups from '@/views/stationview/inventory/memberinventoryview/MemberInventoryGroups.vue'
import {groups, items, member} from './memberinventoryhelp/fixtures'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.inventoryMember.title')" :subtitle="t('helpCenter.inventoryMember.subtitle')">
    <HelpSection :title="t('helpCenter.inventoryMember.whatShown')">
      <p>{{ t('helpCenter.inventoryMember.whatShownText') }}</p>
      <div class="space-y-6">
        <MemberInventoryHeader :member="member"/>
        <MemberInventoryGroups :groups="groups" :items="items" show-exchange-button/>
      </div>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.inventoryMember.tip') }}</HelpTip>
  </HelpArticle>
</template>
