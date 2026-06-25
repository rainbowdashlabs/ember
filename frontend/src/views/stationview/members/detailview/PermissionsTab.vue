/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import type { PermissionGrant, MemberGroup, UserTag } from '@/api/types'

defineProps<{
  memberUserType: string
  memberPermissions: PermissionGrant[]
  memberGroupList: MemberGroup[]
  memberTagList: UserTag[]
}>()

const { t } = useI18n()

function formatUserType(ut: string): string {
  return t('memberEdit.userType' + ut.charAt(0).toUpperCase() + ut.slice(1).toLowerCase())
}
</script>

<template>
  <NeutralContainer class="space-y-3">
    <SubHeader class="text-sm">{{ t('memberDetail.userType') }}</SubHeader>
    <span class="text-sm font-medium">{{ formatUserType(memberUserType) }}</span>
  </NeutralContainer>
  <NeutralContainer v-if="memberPermissions.length > 0" class="space-y-3">
    <SubHeader class="text-sm">{{ t('memberDetail.permissions') }}</SubHeader>
    <div class="flex flex-wrap gap-2">
      <PrimaryBadge v-for="p in memberPermissions" :key="p.id">{{ t(`permissions.${p.permission}.label`) }}</PrimaryBadge>
    </div>
  </NeutralContainer>
  <NeutralContainer v-if="memberGroupList.length > 0" class="space-y-3">
    <SubHeader class="text-sm">{{ t('memberDetail.groups') }}</SubHeader>
    <div class="flex flex-wrap gap-2">
      <SecondaryBadge v-for="g in memberGroupList" :key="g.id">{{ g.name }}</SecondaryBadge>
    </div>
  </NeutralContainer>
  <NeutralContainer v-if="memberTagList.length > 0" class="space-y-3">
    <SubHeader class="text-sm">{{ t('memberDetail.tags') }}</SubHeader>
    <div class="flex flex-wrap gap-2">
      <InfoBadge v-for="tag in memberTagList" :key="tag.id">{{ tag.name }}</InfoBadge>
    </div>
  </NeutralContainer>
</template>
