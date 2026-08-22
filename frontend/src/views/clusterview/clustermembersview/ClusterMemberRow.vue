/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import MemberEditor from './MemberEditor.vue'
import type {ClusterMemberSummary} from '@/api/clusterMembers'
import {ClusterUserType} from '@/api/clusters'

defineProps<{
  member: ClusterMemberSummary
  editable: boolean
  busy: boolean
  open: boolean
}>()

const emit = defineEmits<{
  remove: [memberId: number]
  userType: [memberId: number, userType: string]
  toggle: [memberId: number]
  saved: []
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-3">
    <div class="flex flex-wrap items-center justify-between gap-3">
      <div>
        <p class="font-medium">{{ member.name ?? t('common.unknown') }}</p>
        <p class="text-sm text-(--text-muted)">{{ member.email }}</p>
      </div>
      <div class="flex items-center gap-2">
        <SelectInput
            :disabled="!editable || busy"
            :model-value="member.userType"
            @update:model-value="v => emit('userType', member.id, String(v))"
        >
          <option :value="ClusterUserType.CLUSTER_USER">{{ t('clusterOverview.role.CLUSTER_USER') }}</option>
          <option :value="ClusterUserType.CLUSTER_ADMIN">{{ t('clusterOverview.role.CLUSTER_ADMIN') }}</option>
        </SelectInput>
        <SecondaryButton @click="emit('toggle', member.id)">
          {{ open ? t('common.close') : t('common.edit') }}
        </SecondaryButton>
        <DeleteButton v-if="editable" :disabled="busy" @click="emit('remove', member.id)"/>
      </div>
    </div>

    <MemberEditor v-if="open" :editable="editable" :member-id="member.id" @saved="emit('saved')"/>
  </NeutralContainer>
</template>
