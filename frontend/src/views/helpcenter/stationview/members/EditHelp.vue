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
import TabBar from '@/components/navigation/TabBar.vue'
import {ref} from 'vue'

const {t} = useI18n()

const activeTab = ref('general')
const tabs = [
  {key: 'general', label: t('memberEdit.tabGeneral')},
  {key: 'profile', label: t('memberEdit.tabProfile')},
  {key: 'notes', label: t('memberEdit.tabNotes')},
]
</script>

<template>
  <HelpArticle :title="t('helpCenter.membersEdit.title')" :subtitle="t('helpCenter.membersEdit.subtitle')">
    <HelpSection :title="t('helpCenter.membersEdit.whatShown')">
      <p>{{ t('helpCenter.membersEdit.whatShownText') }}</p>
    </HelpSection>

    <!-- Dummy: Tab bar -->
    <TabBar v-model="activeTab" :tabs="tabs"/>

    <HelpSection :title="t('helpCenter.membersEdit.generalTitle')">
      <p>{{ t('helpCenter.membersEdit.generalText') }}</p>
      <p>{{ t('helpCenter.membersEdit.generalImmediate') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.membersEdit.accountTitle')">
      <p>{{ t('helpCenter.membersEdit.accountText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.membersEdit.fieldsTitle')">
      <p>{{ t('helpCenter.membersEdit.fieldsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.membersEdit.notesTitle')">
      <p>{{ t('helpCenter.membersEdit.notesText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.membersEdit.tip') }}</HelpTip>
  </HelpArticle>
</template>
