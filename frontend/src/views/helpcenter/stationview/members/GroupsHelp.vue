/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import HelpTip from '@/components/helpcenter/HelpTip.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import PermissionPicker from '@/components/input/PermissionPicker.vue'
import GroupListPanel from '@/components/groups/GroupListPanel.vue'
import GroupDetailPanel from '@/components/groups/GroupDetailPanel.vue'
import GroupRulesFields from '@/views/stationview/members/groupsview/GroupRulesFields.vue'
import GroupSetPanel from '@/views/stationview/members/groupsview/GroupSetPanel.vue'
import {StationUserType} from '@/api/types'
import type {StationUserType as StationUserTypeName} from '@/api/generated/schema'
import {
  DEMO_GROUP_GRANTS,
  DEMO_PERMISSIONS,
  demoCandidates,
  demoGroupMembers,
  demoGroups,
  demoGroupSets,
} from './fixtures'

const {t} = useI18n()
const groups = demoGroups(t)
const sets = demoGroupSets(t)
const groupMembers = demoGroupMembers(t)
const candidates = demoCandidates(t)
const offeredUserTypes = [StationUserType.MEMBER]
const grantedIds = new Set(DEMO_GROUP_GRANTS.map(grant => grant.id))
const setId = ref<number | null>(1)
const boundUserTypes = ref<StationUserTypeName[]>([StationUserType.MEMBER])
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
        <GroupListPanel :groups="groups" :selected-group="groups[0] ?? null" :sets="sets" can-convert-to-tag/>
        <GroupDetailPanel v-if="groups[0]" :selected-group="groups[0]" :group-loading="false"
                          :sorted-group-members="groupMembers" :available-members="candidates"
                          :offered-user-types="offeredUserTypes" :group-roles="DEMO_GROUP_GRANTS"
                          :all-roles="DEMO_PERMISSIONS" :group-role-ids="grantedIds" can-edit-roles/>
      </div>
    </HelpSection>

    <HelpSection :title="t('helpCenter.membersGroups.rolesTitle')">
      <p>{{ t('helpCenter.membersGroups.rolesText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.membersGroups.permissionsExampleTitle')">
      <NeutralContainer class="space-y-2">
        <FieldLabel class="text-(--text-muted)">{{ t('memberGroups.tabPermissions') }}</FieldLabel>
        <PermissionPicker :model-value="grantedIds" :all-roles="DEMO_PERMISSIONS"/>
        <MutedText size="sm">{{ t('helpCenter.membersGroups.permissionsHint') }}</MutedText>
      </NeutralContainer>
    </HelpSection>

    <HelpSection :title="t('helpCenter.membersGroups.joinRightsTitle')">
      <p>{{ t('helpCenter.membersGroups.joinRightsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.membersGroups.setsTitle')">
      <p>{{ t('helpCenter.membersGroups.setsText') }}</p>
      <p>{{ t('helpCenter.membersGroups.setsDelete') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.membersGroups.setsExampleTitle')">
      <GroupSetPanel :sets="sets"/>
    </HelpSection>

    <HelpSection :title="t('helpCenter.membersGroups.moveTitle')">
      <p>{{ t('helpCenter.membersGroups.moveText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.membersGroups.bindingTitle')">
      <p>{{ t('helpCenter.membersGroups.bindingText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.membersGroups.bindingExampleTitle')">
      <NeutralContainer class="space-y-4">
        <GroupRulesFields v-model:set-id="setId" v-model:user-types="boundUserTypes" :sets="sets"/>
      </NeutralContainer>
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
