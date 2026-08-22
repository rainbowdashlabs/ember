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
import PermissionPicker from '@/components/input/PermissionPicker.vue'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import {clusterMembers, data} from '@/api'
import type {ClusterGroupSummary} from '@/api/clusterMembers'
import type {PermissionGrant} from '@/api/types'
import type {PermissionNode} from '@/api/data'
import {highestOf} from '@/api/data'

const props = defineProps<{
  memberId: number
  editable: boolean
}>()

const emit = defineEmits<{
  saved: []
}>()

const {t} = useI18n()

const loading = ref(true)
const saving = ref(false)
const direct = ref<string[]>([])
const groupIds = ref<number[]>([])
const groups = ref<ClusterGroupSummary[]>([])
const resolved = ref<string[]>([])
const hierarchy = ref<PermissionNode[]>([])

/**
 * The association's permissions in the shape the shared picker speaks.
 *
 * The picker identifies a selection by a numeric grant id, because a station's permissions are rows. An
 * association's are not: the API speaks their names throughout. Numbering them here is what lets the one
 * picker draw both, and the numbers never leave this component.
 */
const grants = computed<PermissionGrant[]>(() =>
    hierarchy.value.map((node, index) => ({id: index + 1, permission: node.name})))
const idByName = computed(() => new Map(grants.value.map(g => [g.permission, g.id])))
const nameById = computed(() => new Map(grants.value.map(g => [g.id, g.permission])))

const selected = computed<Set<number>>({
  get: () => new Set(direct.value.map(p => idByName.value.get(p)).filter((id): id is number => !!id)),
  set: next => {
    direct.value = [...next].map(id => nameById.value.get(id)).filter((n): n is string => !!n)
  },
})

/**
 * What this person ends up holding once their type, their own grants and their groups are put together,
 * with everything an ancestor already carries left out.
 */
const effective = computed(() =>
    highestOf(resolved.value.filter(p => p !== 'USER' && p !== 'LOGIN'), hierarchy.value).sort())

async function load() {
  loading.value = true
  const [detail, all, tree] = await Promise.all([
    clusterMembers.getMember(props.memberId),
    clusterMembers.listGroups(),
    data.getClusterPermissionHierarchy().catch(() => []),
  ])
  hierarchy.value = tree
  direct.value = [...detail.direct]
  groupIds.value = detail.groups.map(g => g.id)
  resolved.value = [...detail.resolved]
  groups.value = all
  loading.value = false
}

onMounted(load)
watch(() => props.memberId, load)

function toggleGroup(groupId: number) {
  groupIds.value = groupIds.value.includes(groupId)
      ? groupIds.value.filter(id => id !== groupId)
      : [...groupIds.value, groupId]
}

async function save() {
  saving.value = true
  try {
    await clusterMembers.setMemberPermissions(props.memberId, direct.value)
    await clusterMembers.setMemberGroups(props.memberId, groupIds.value)
    await load()
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
      <FormLabel>{{ t('clusterMembers.permissionsLabel') }}</FormLabel>
      <p class="text-xs text-(--text-muted)">{{ t('clusterMembers.permissionsHint') }}</p>
      <fieldset :disabled="!editable" class="contents">
        <PermissionPicker v-model="selected" :all-roles="grants" scope="cluster"/>
      </fieldset>
    </div>

    <div class="space-y-1">
      <FormLabel>{{ t('clusterMembers.groupsLabel') }}</FormLabel>
      <div class="flex flex-wrap gap-2">
        <SelectionToggleButton
            v-for="group in groups"
            :key="group.id"
            :selected="groupIds.includes(group.id)"
            @toggle="editable && toggleGroup(group.id)"
        >
          {{ group.name }}
        </SelectionToggleButton>
        <span v-if="groups.length === 0" class="text-xs text-(--text-muted)">{{ t('clusterMembers.noGroups') }}</span>
      </div>
    </div>

    <div class="space-y-1">
      <FormLabel>{{ t('clusterMembers.effectiveLabel') }}</FormLabel>
      <div v-if="effective.length" class="flex flex-wrap gap-1">
        <PrimaryBadge v-for="permission in effective" :key="permission">
          {{ t(`permissions.${permission}.label`) }}
        </PrimaryBadge>
      </div>
      <p v-else class="text-xs text-(--text-muted)">{{ t('clusterMembers.effectiveNone') }}</p>
    </div>

    <PrimaryButton v-if="editable" :disabled="saving" @click="save">{{ t('common.save') }}</PrimaryButton>
  </div>
</template>
