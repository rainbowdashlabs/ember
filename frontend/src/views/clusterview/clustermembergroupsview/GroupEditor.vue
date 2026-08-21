/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import Spinner from '@/components/feedback/Spinner.vue'
import FormLabel from '@/components/input/FormLabel.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import {clusterMembers} from '@/api'
import type {ClusterMemberSummary} from '@/api/clusterMembers'
import {ClusterPermission} from '@/api/clusters'

const props = defineProps<{
  groupId: number
  editable: boolean
}>()

const emit = defineEmits<{
  saved: []
}>()

const {t} = useI18n()

/** Every permission a cluster can hand out, minus the two that everybody has anyway. */
const GRANTABLE = Object.values(ClusterPermission).filter(p => p !== 'USER' && p !== 'LOGIN')

const loading = ref(true)
const saving = ref(false)
const name = ref('')
const permissions = ref<string[]>([])
const memberIds = ref<number[]>([])
const members = ref<ClusterMemberSummary[]>([])

async function load() {
  loading.value = true
  const [detail, all] = await Promise.all([clusterMembers.getGroup(props.groupId), clusterMembers.listMembers()])
  name.value = detail.name
  permissions.value = [...detail.permissions]
  memberIds.value = [...detail.memberIds]
  members.value = all
  loading.value = false
}

onMounted(load)
watch(() => props.groupId, load)

function togglePermission(permission: string, on: boolean) {
  permissions.value = on
      ? [...new Set([...permissions.value, permission])]
      : permissions.value.filter(p => p !== permission)
}

function toggleMember(memberId: number, on: boolean) {
  memberIds.value = on
      ? [...new Set([...memberIds.value, memberId])]
      : memberIds.value.filter(id => id !== memberId)
}

async function save() {
  saving.value = true
  try {
    await clusterMembers.updateGroup(props.groupId, {
      name: name.value.trim(),
      permissions: permissions.value,
      memberIds: memberIds.value,
    })
    emit('saved')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <Spinner v-if="loading"/>

  <div v-else class="space-y-4 border-t border-(--border) pt-3">
    <div class="space-y-1">
      <FormLabel>{{ t('clusterMemberGroups.nameLabel') }}</FormLabel>
      <TextInput v-model="name" :disabled="!editable"/>
    </div>

    <div class="space-y-1">
      <FormLabel>{{ t('clusterMemberGroups.permissionsLabel') }}</FormLabel>
      <div class="grid grid-cols-1 gap-1 sm:grid-cols-2">
        <label v-for="permission in GRANTABLE" :key="permission" class="flex items-center gap-2 text-sm">
          <CheckboxInput
              :disabled="!editable"
              :model-value="permissions.includes(permission)"
              @update:model-value="v => togglePermission(permission, v)"
          />
          <span>{{ permission }}</span>
        </label>
      </div>
    </div>

    <div class="space-y-1">
      <FormLabel>{{ t('clusterMemberGroups.membersLabel') }}</FormLabel>
      <label v-for="member in members" :key="member.id" class="flex items-center gap-2 text-sm">
        <CheckboxInput
            :disabled="!editable"
            :model-value="memberIds.includes(member.id)"
            @update:model-value="v => toggleMember(member.id, v)"
        />
        <span>{{ member.name ?? member.email }}</span>
      </label>
    </div>

    <PrimaryButton v-if="editable" :disabled="saving" @click="save">{{ t('common.save') }}</PrimaryButton>
  </div>
</template>
