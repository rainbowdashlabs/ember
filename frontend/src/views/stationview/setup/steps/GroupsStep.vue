/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, onMounted, reactive, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import SetupLayout from '@/views/stationview/setup/SetupLayout.vue'
import Alert from '@/components/feedback/Alert.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import InfoContainer from '@/components/container/InfoContainer.vue'
import MutedText from '@/components/typography/MutedText.vue'
import GroupList from './groupsstep/GroupList.vue'
import GroupEditor from './groupsstep/GroupEditor.vue'
import {memberGroups, stationMembers} from '@/api'
import type {MemberGroup, PermissionGrant} from '@/api/types'
import {useSetupStatus} from '@/composables/useSetupStatus'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {nextStep, stepRouteName} from '@/views/stationview/setup/steps'
import {apiErrorMessage} from '@/util/apiError'

const {t} = useI18n()
const router = useRouter()
const {reload} = useSetupStatus()

const groups = ref<MemberGroup[]>([])
const draft = ref('')
const loading = ref(true)
const error = ref('')

const allRoles = ref<PermissionGrant[]>([])
const permissionsByGroup = reactive<Record<number, Set<number>>>({})
const permissionLoading = reactive<Record<number, boolean>>({})
const selectedId = ref<number | null>(null)
const colorDraft = ref<string>('')

const selectedGroup = computed(() => groups.value.find((g) => g.id === selectedId.value) ?? null)

onMounted(async () => {
    try {
        const [groupsRes, rolesRes] = await Promise.all([
            memberGroups.listGroups(),
            stationMembers.listAllPermissions(),
        ])
        groups.value = sortByPosition(groupsRes)
        allRoles.value = rolesRes
        const firstGroup = groups.value[0]
        if (firstGroup) await selectGroup(firstGroup.id)
    } catch {
        error.value = t('common.error')
    } finally {
        loading.value = false
    }
})

function sortByPosition(list: MemberGroup[]): MemberGroup[] {
    return [...list].sort((a, b) => (a.position ?? 0) - (b.position ?? 0))
}

const {running: adding, error: addError, run: runAddGroup} = useAsyncAction(async () => {
    const nextPosition = (groups.value[groups.value.length - 1]?.position ?? -1) + 1
    const created = await memberGroups.createGroup({name: draft.value.trim(), position: nextPosition})
    groups.value = sortByPosition([...groups.value, created])
    permissionsByGroup[created.id] = new Set()
    draft.value = ''
    await selectGroup(created.id)
})

const displayError = computed(() => error.value || addError.value)

function addGroup() {
    if (!draft.value.trim()) return
    error.value = ''
    return runAddGroup()
}

async function removeGroup(id: number) {
    try {
        await memberGroups.deleteGroup(id)
        groups.value = groups.value.filter((g) => g.id !== id)
        delete permissionsByGroup[id]
        if (selectedId.value === id) {
            selectedId.value = groups.value[0]?.id ?? null
            if (selectedId.value) await selectGroup(selectedId.value)
        }
    } catch {
        error.value = t('common.error')
    }
}

async function selectGroup(id: number) {
    selectedId.value = id
    colorDraft.value = selectedGroup.value?.color ?? ''
    if (!(id in permissionsByGroup)) {
        permissionLoading[id] = true
        try {
            const grants = await memberGroups.getGroupPermissions(id)
            permissionsByGroup[id] = new Set(grants.map((g) => g.id))
        } catch {
            error.value = t('common.error')
            permissionsByGroup[id] = new Set()
        } finally {
            permissionLoading[id] = false
        }
    }
}

async function persistGroup(group: MemberGroup, patch: Partial<MemberGroup>) {
    try {
        const updated = await memberGroups.updateGroup(group.id, {
            name: patch.name ?? group.name,
            color: patch.color !== undefined ? (patch.color || null) : group.color,
            position: patch.position ?? group.position,
        })
        groups.value = sortByPosition(groups.value.map((g) => (g.id === group.id ? {...g, ...updated} : g)))
    } catch {
        error.value = t('common.error')
    }
}

async function onColorChange(color: string) {
    colorDraft.value = color
    if (!selectedGroup.value) return
    await persistGroup(selectedGroup.value, {color})
}

async function moveGroup(id: number, delta: -1 | 1) {
    const idx = groups.value.findIndex((g) => g.id === id)
    if (idx < 0) return
    const swapIdx = idx + delta
    const a = groups.value[idx]
    const b = groups.value[swapIdx]
    if (!a || !b) return
    const posA = a.position ?? idx
    const posB = b.position ?? swapIdx
    await Promise.all([persistGroup(a, {position: posB}), persistGroup(b, {position: posA})])
}

async function onPermissionsChange(groupId: number, newIds: Set<number>) {
    permissionsByGroup[groupId] = newIds
    try {
        await memberGroups.setGroupPermissions(groupId, {permissionIds: [...newIds]})
    } catch (e: unknown) {
        error.value = apiErrorMessage(e) || t('common.error')
    }
}

const {running: saving, run: save} = useAsyncAction(async () => {
    await reload()
    const next = nextStep('groups')
    if (next) router.push({name: stepRouteName(next)})
})
</script>

<template>
  <SetupLayout step-id="groups" skippable :saving="saving || adding" @save="save">
    <Alert v-if="displayError" variant="error">{{ displayError }}</Alert>

    <InfoContainer class="space-y-2">
      <p class="font-medium text-sm">{{ t('setup.steps.groups.aboutTitle') }}</p>
      <p class="text-sm">{{ t('setup.steps.groups.aboutBody') }}</p>
      <ul class="list-disc list-inside text-sm space-y-1">
        <li>{{ t('setup.steps.groups.useCase1') }}</li>
        <li>{{ t('setup.steps.groups.useCase2') }}</li>
        <li>{{ t('setup.steps.groups.useCase3') }}</li>
      </ul>
      <p class="text-sm pt-2">{{ t('setup.steps.groups.colorExplainer') }}</p>
      <p class="text-sm">{{ t('setup.steps.groups.sortExplainer') }}</p>
    </InfoContainer>

    <Spinner v-if="loading" size="lg"/>
    <div v-else class="grid gap-6 lg:grid-cols-2">
      <GroupList
          v-model:draft="draft"
          :groups="groups"
          :selected-id="selectedId"
          @select="selectGroup"
          @move="moveGroup"
          @remove="removeGroup"
          @add="addGroup"
      />

      <GroupEditor
          v-if="selectedGroup"
          :group="selectedGroup"
          :color="colorDraft"
          :all-roles="allRoles"
          :permissions="permissionsByGroup[selectedGroup.id] ?? new Set()"
          :permissions-loading="permissionLoading[selectedGroup.id] ?? false"
          @color-change="onColorChange"
          @permissions-change="ids => onPermissionsChange(selectedGroup!.id, ids)"
      />
      <MutedText v-else size="sm">{{ t('setup.steps.groups.selectHint') }}</MutedText>
    </div>
  </SetupLayout>
</template>
