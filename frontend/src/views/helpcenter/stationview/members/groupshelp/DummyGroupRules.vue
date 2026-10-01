/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import GroupRulesFields from '@/views/stationview/members/groupsview/GroupRulesFields.vue'
import GroupSetPanel from '@/views/stationview/members/groupsview/GroupSetPanel.vue'
import {StationUserType} from '@/api/types'
import type {MemberGroupSet, StationUserType as StationUserTypeName} from '@/api/generated/schema'

/** The sets of groups and a group's rules as the groups page shows them, filled with a sample. */
defineProps<{
  /** Which half to show: the list of sets, or the rules in the group form. */
  part: 'sets' | 'rules'
}>()

const {t} = useI18n()

const sets = computed<MemberGroupSet[]>(() => [{id: 1, stationId: '', name: t('helpCenter.sample.groups.levels')}])
const setId = ref<number | null>(1)
const userTypes = ref<StationUserTypeName[]>([StationUserType.MEMBER])
</script>

<template>
  <GroupSetPanel v-if="part === 'sets'" :sets="sets"/>
  <NeutralContainer v-else class="space-y-4">
    <GroupRulesFields v-model:set-id="setId" v-model:user-types="userTypes" :sets="sets"/>
  </NeutralContainer>
</template>
