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
import DummyGroupsList from './groupshelp/DummyGroupsList.vue'
import DummyGroupDetailPanel from './groupshelp/DummyGroupDetailPanel.vue'
import DummyGroupPermissions from './groupshelp/DummyGroupPermissions.vue'
import DummyGroupRules from './groupshelp/DummyGroupRules.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.membersGroups.title')" :subtitle="t('helpCenter.membersGroups.subtitle')">
    <HelpSection :title="t('helpCenter.membersGroups.whatIs')">
      <p>{{ t('helpCenter.membersGroups.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.membersGroups.createTitle')">
      <p>{{ t('helpCenter.membersGroups.create') }}</p>
      <p>{{ t('helpCenter.membersGroups.select') }}</p>
      <p>{{ t('helpCenter.membersGroups.addMember') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.membersGroups.exampleTitle')">
      <div class="grid gap-6 lg:grid-cols-2">
        <DummyGroupsList/>
        <DummyGroupDetailPanel/>
      </div>
    </HelpSection>

    <HelpSection :title="t('helpCenter.membersGroups.rolesTitle')">
      <p>{{ t('helpCenter.membersGroups.rolesText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.membersGroups.permissionsExampleTitle')">
      <DummyGroupPermissions/>
    </HelpSection>

    <HelpSection :title="t('helpCenter.membersGroups.joinRightsTitle')">
      <p>{{ t('helpCenter.membersGroups.joinRightsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.membersGroups.setsTitle')">
      <p>{{ t('helpCenter.membersGroups.setsText') }}</p>
      <p>{{ t('helpCenter.membersGroups.setsDelete') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.membersGroups.setsExampleTitle')">
      <DummyGroupRules part="sets"/>
    </HelpSection>

    <HelpSection :title="t('helpCenter.membersGroups.moveTitle')">
      <p>{{ t('helpCenter.membersGroups.moveText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.membersGroups.bindingTitle')">
      <p>{{ t('helpCenter.membersGroups.bindingText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.membersGroups.bindingExampleTitle')">
      <DummyGroupRules part="rules"/>
    </HelpSection>

    <HelpSection :title="t('helpCenter.membersGroups.conflictTitle')">
      <p>{{ t('helpCenter.membersGroups.conflictText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.membersGroups.colorTitle')">
      <p>{{ t('helpCenter.membersGroups.colorText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.membersGroups.convertTitle')">
      <p>{{ t('helpCenter.membersGroups.convertText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.membersGroups.tip') }}</HelpTip>
  </HelpArticle>
</template>
