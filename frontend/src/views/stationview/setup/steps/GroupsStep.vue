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
import TextInput from '@/components/input/text/TextInput.vue'
import ColorInput from '@/components/input/ColorInput.vue'
import Alert from '@/components/feedback/Alert.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import InfoContainer from '@/components/container/InfoContainer.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import PermissionPicker from '@/components/input/PermissionPicker.vue'
import {memberGroups, stationMembers} from '@/api'
import type {MemberGroup, PermissionGrant} from '@/api/types'
import {useSetupStatus} from '@/composables/useSetupStatus'
import {nextStep, stepRouteName} from '@/views/stationview/setup/steps'

const {t} = useI18n()
const router = useRouter()
const {reload} = useSetupStatus()

const groups = ref<MemberGroup[]>([])
const draft = ref('')
const loading = ref(true)
const saving = ref(false)
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
        if (groups.value.length > 0) await selectGroup(groups.value[0].id)
    } catch {
        error.value = t('common.error')
    } finally {
        loading.value = false
    }
})

function sortByPosition(list: MemberGroup[]): MemberGroup[] {
    return [...list].sort((a, b) => (a.position ?? 0) - (b.position ?? 0))
}

async function addGroup() {
    if (!draft.value.trim()) return
    saving.value = true
    error.value = ''
    try {
        const nextPosition = (groups.value[groups.value.length - 1]?.position ?? -1) + 1
        const created = await memberGroups.createGroup({name: draft.value.trim(), position: nextPosition})
        groups.value = sortByPosition([...groups.value, created])
        permissionsByGroup[created.id] = new Set()
        draft.value = ''
        await selectGroup(created.id)
    } catch {
        error.value = t('common.error')
    } finally {
        saving.value = false
    }
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
    if (swapIdx < 0 || swapIdx >= groups.value.length) return
    const a = groups.value[idx]
    const b = groups.value[swapIdx]
    const posA = a.position ?? idx
    const posB = b.position ?? swapIdx
    await Promise.all([persistGroup(a, {position: posB}), persistGroup(b, {position: posA})])
}

async function onPermissionsChange(groupId: number, newIds: Set<number>) {
    permissionsByGroup[groupId] = newIds
    try {
        await memberGroups.setGroupPermissions(groupId, {permissionIds: [...newIds]})
    } catch (e: unknown) {
        const msg = (e as {response?: {data?: {message?: string}}})?.response?.data?.message
        error.value = msg || t('common.error')
    }
}

async function save() {
    saving.value = true
    try {
        await reload()
        const next = nextStep('groups')
        if (next) router.push({name: stepRouteName(next)})
    } finally {
        saving.value = false
    }
}
</script>

<template>
  <SetupLayout step-id="groups" skippable :saving="saving" @save="save">
    <Alert v-if="error" variant="error">{{ error }}</Alert>

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
      <!-- Left: list of groups -->
      <div class="space-y-3">
        <SectionHeader>{{ t('setup.steps.groups.listTitle') }}</SectionHeader>
        <ul v-if="groups.length > 0" class="space-y-2">
          <li v-for="(g, idx) in groups" :key="g.id">
            <NeutralContainer
                :class="selectedId === g.id ? 'border-primary' : 'hover:border-primary'"
                class="cursor-pointer transition-colors"
                @click="selectGroup(g.id)"
            >
              <div class="flex items-center gap-2">
                <span
                    v-if="g.color"
                    class="inline-block h-3 w-3 rounded-full shrink-0"
                    :style="{backgroundColor: g.color}"
                />
                <span v-else class="inline-block h-3 w-3 rounded-full border border-(--border) shrink-0"/>
                <span class="flex-1 font-medium truncate">{{ g.name }}</span>
                <IconButton
                    :icon="['fas', 'chevron-up']"
                    :label="t('setup.steps.groups.moveUp')"
                    :disabled="idx === 0"
                    @click.stop="moveGroup(g.id, -1)"
                />
                <IconButton
                    :icon="['fas', 'chevron-down']"
                    :label="t('setup.steps.groups.moveDown')"
                    :disabled="idx === groups.length - 1"
                    @click.stop="moveGroup(g.id, 1)"
                />
                <DeleteButton :title="t('common.delete')" @click.stop="removeGroup(g.id)"/>
              </div>
            </NeutralContainer>
          </li>
        </ul>
        <MutedText v-else size="sm">{{ t('setup.steps.groups.emptyHint') }}</MutedText>

        <form class="flex items-center gap-2" @submit.prevent="addGroup">
          <TextInput v-model="draft" :placeholder="t('setup.steps.groups.placeholder')" class="flex-1"/>
          <SecondaryButton type="submit">{{ t('setup.actions.addRow') }}</SecondaryButton>
        </form>
      </div>

      <!-- Right: selected group editor -->
      <div v-if="selectedGroup" class="space-y-4">
        <SectionHeader>{{ selectedGroup.name }}</SectionHeader>

        <div class="space-y-1">
          <FieldLabel>{{ t('memberGroups.color') }}</FieldLabel>
          <div class="flex items-center gap-2">
            <ColorInput :model-value="colorDraft" @update:model-value="onColorChange"/>
            <SecondaryButton v-if="colorDraft" compact @click="onColorChange('')">
              <font-awesome-icon :icon="['fas', 'xmark']"/>
            </SecondaryButton>
            <MutedText size="sm">{{ t('memberGroups.colorHint') }}</MutedText>
          </div>
        </div>

        <div class="space-y-1">
          <FieldLabel>{{ t('setup.steps.groups.permissionsTitle') }}</FieldLabel>
          <Spinner v-if="permissionLoading[selectedGroup.id]" size="md"/>
          <PermissionPicker
              v-else
              :model-value="permissionsByGroup[selectedGroup.id] ?? new Set()"
              :all-roles="allRoles"
              @update:model-value="(ids) => onPermissionsChange(selectedGroup.id, ids)"
          />
        </div>
      </div>
      <MutedText v-else size="sm">{{ t('setup.steps.groups.selectHint') }}</MutedText>
    </div>
  </SetupLayout>
</template>
