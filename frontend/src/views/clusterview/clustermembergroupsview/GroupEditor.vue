/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import Spinner from '@/components/feedback/Spinner.vue'
import FormLabel from '@/components/input/FormLabel.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import PermissionPicker from '@/components/input/PermissionPicker.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import {clusterMembers, data} from '@/api'
import type {ClusterMemberSummary} from '@/api/clusterMembers'
import type {PermissionGrant} from '@/api/types'

const props = defineProps<{
  groupId: number
  editable: boolean
}>()

const emit = defineEmits<{
  saved: []
}>()

const {t} = useI18n()

const loading = ref(true)
const saving = ref(false)
const name = ref('')
const permissions = ref<string[]>([])
const memberIds = ref<number[]>([])
const members = ref<ClusterMemberSummary[]>([])

/**
 * The association's permissions in the shape the shared picker speaks.
 *
 * The picker identifies a selection by a numeric grant id, because a station's permissions are rows. An
 * association's are not: the API speaks their names throughout. Numbering them here is what lets the one
 * picker draw both, and the numbers never leave this component.
 */
const grants = ref<PermissionGrant[]>([])
const idByName = computed(() => new Map(grants.value.map(g => [g.permission, g.id])))
const nameById = computed(() => new Map(grants.value.map(g => [g.id, g.permission])))

const selected = computed<Set<number>>({
  get: () => new Set(permissions.value.map(p => idByName.value.get(p)).filter((id): id is number => !!id)),
  set: next => {
    permissions.value = [...next].map(id => nameById.value.get(id)).filter((n): n is string => !!n)
  },
})

async function load() {
  loading.value = true
  const [detail, all, hierarchy] = await Promise.all([
    clusterMembers.getGroup(props.groupId),
    clusterMembers.listMembers(),
    data.getClusterPermissionHierarchy().catch(() => []),
  ])
  grants.value = hierarchy.map((node, index) => ({id: index + 1, permission: node.name}))
  name.value = detail.name
  permissions.value = [...detail.permissions]
  memberIds.value = [...detail.memberIds]
  members.value = all
  loading.value = false
}

onMounted(load)
watch(() => props.groupId, load)

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
      <fieldset :disabled="!editable" class="contents">
        <PermissionPicker v-model="selected" :all-roles="grants" scope="cluster"/>
      </fieldset>
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
