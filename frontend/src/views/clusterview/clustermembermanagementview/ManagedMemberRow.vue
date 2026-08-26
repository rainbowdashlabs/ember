/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import type {ManagedMember} from '@/api/clusterMembers'

defineProps<{
  member: ManagedMember
  busy: boolean
}>()

const emit = defineEmits<{
  userType: [memberId: number, userType: string]
  archive: [memberId: number]
}>()

const {t} = useI18n()

/** The types a station's people can have, in the order the station's own screens list them. */
const USER_TYPES = ['MEMBER', 'GUARDIAN', 'TEAM', 'MANAGER']
</script>

<template>
  <NeutralContainer class="flex flex-wrap items-center justify-between gap-3">
    <div class="min-w-0">
      <p class="font-medium truncate">
        {{ member.name || member.email }}
        <SecondaryBadge v-if="member.former" class="ml-2">{{ t('clusterMemberManagement.former') }}</SecondaryBadge>
        <SecondaryBadge v-if="member.stationOwner" class="ml-2">
          {{ t('clusterMemberManagement.stationOwner') }}
        </SecondaryBadge>
      </p>
      <p class="text-sm text-(--text-muted) truncate">{{ member.stationName }}</p>
    </div>

    <div class="flex items-center gap-2">
      <SelectInput
          :disabled="busy || member.stationOwner"
          :model-value="member.userType"
          @update:model-value="v => emit('userType', member.id, String(v))"
      >
        <option v-for="type in USER_TYPES" :key="type" :value="type">
          {{ t(`clusterMemberManagement.userTypes.${type}`) }}
        </option>
      </SelectInput>
      <SecondaryButton
          v-if="!member.former && !member.stationOwner"
          :disabled="busy"
          @click="emit('archive', member.id)"
      >
        {{ t('clusterMemberManagement.archive') }}
      </SecondaryButton>
    </div>
  </NeutralContainer>
</template>
