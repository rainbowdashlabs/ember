/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import SetupLayout from '@/views/stationview/setup/SetupLayout.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import Alert from '@/components/feedback/Alert.vue'
import {memberGroups, stationMemberInvites} from '@/api'
import type {MemberGroup} from '@/api/types'
import type {GuardianRequest, InviteEntry} from '@/api/stationMemberInvites'
import {useSetupStatus} from '@/composables/useSetupStatus'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {nextStep, stepRouteName} from '@/views/stationview/setup/steps'

const {t} = useI18n()
const router = useRouter()
const {reload} = useSetupStatus()

const tab = ref<'bulk' | 'rich' | 'csv'>('rich')

const bulkText = ref('')
const bulkUserType = ref('MEMBER')
const bulkGroupId = ref<number | null>(null)

interface RichRow {
    firstName: string
    lastName: string
    email: string
    userType: string
    groupId: number | null
    guardians: GuardianRequest[]
}

const richRows = ref<RichRow[]>([])
const groups = ref<MemberGroup[]>([])
const successCount = ref(0)

const USER_TYPES = ['MEMBER', 'TEAM', 'MANAGER', 'GUARDIAN']

function openMemberImport() {
    const next = nextStep('invites')
    const returnTo = next ? router.resolve({name: stepRouteName(next)}).href : '/station/setup'
    router.push({path: '/station/members/import', query: {returnTo}})
}

onMounted(async () => {
    try {
        groups.value = await memberGroups.listGroups()
    } catch { /* ignore */ }
    if (richRows.value.length === 0) addRichRow()
})

function addRichRow() {
    richRows.value.push({
        firstName: '',
        lastName: '',
        email: '',
        userType: 'MEMBER',
        groupId: null,
        guardians: [],
    })
}

function removeRichRow(idx: number) {
    richRows.value.splice(idx, 1)
}

function addGuardian(rowIdx: number) {
    richRows.value[rowIdx].guardians.push({firstName: '', lastName: '', email: ''})
}

function removeGuardian(rowIdx: number, gIdx: number) {
    richRows.value[rowIdx].guardians.splice(gIdx, 1)
}

const expandedBulk = computed(() => {
    const lines = bulkText.value.split(/[,\n]/).map((s) => s.trim()).filter(Boolean)
    return lines.map((email) => ({
        firstName: email.split('@')[0],
        lastName: '',
        email,
        userType: bulkUserType.value,
        groupId: bulkGroupId.value,
        guardians: [] as GuardianRequest[],
    }))
})

const {running: saving, error, run: runSave, clearError} = useAsyncAction(async (payload: InviteEntry[]) => {
    const result = await stationMemberInvites.createInvites({invites: payload})
    successCount.value = result.provisioned.length
    await reload()
    const next = nextStep('invites')
    if (next) router.push({name: stepRouteName(next)})
})

function save() {
    clearError()
    successCount.value = 0
    const payload: InviteEntry[] = tab.value === 'bulk'
        ? expandedBulk.value
        : richRows.value.filter((r) => r.email.trim() !== '')
    if (payload.length === 0) {
        const next = nextStep('invites')
        if (next) router.push({name: stepRouteName(next)})
        return
    }
    return runSave(payload)
}
</script>

<template>
  <SetupLayout step-id="invites" skippable :saving="saving" @save="save">
    <Alert v-if="error" variant="error">{{ error }}</Alert>
    <div class="flex gap-2 text-sm">
      <SelectionToggleButton :selected="tab === 'rich'" @toggle="tab = 'rich'">
        {{ t('setup.steps.invites.tabRich') }}
      </SelectionToggleButton>
      <SelectionToggleButton :selected="tab === 'bulk'" @toggle="tab = 'bulk'">
        {{ t('setup.steps.invites.tabBulk') }}
      </SelectionToggleButton>
      <SelectionToggleButton :selected="tab === 'csv'" @toggle="tab = 'csv'">
        {{ t('setup.steps.invites.tabCsv') }}
      </SelectionToggleButton>
    </div>

    <div v-if="tab === 'bulk'" class="space-y-3">
      <label class="block text-sm">
        {{ t('setup.steps.invites.bulkEmails') }}
        <TextAreaInput v-model="bulkText" :placeholder="t('setup.steps.invites.bulkPlaceholder')" rows="4"/>
      </label>
      <div class="flex gap-3">
        <label class="block text-sm flex-1">
          {{ t('setup.steps.invites.userType') }}
          <SelectInput v-model="bulkUserType">
            <option v-for="ut in USER_TYPES" :key="ut" :value="ut">{{ ut }}</option>
          </SelectInput>
        </label>
        <label class="block text-sm flex-1">
          {{ t('setup.steps.invites.group') }}
          <SelectInput v-model="bulkGroupId">
            <option :value="null">{{ t('setup.steps.invites.groupNone') }}</option>
            <option v-for="g in groups" :key="g.id" :value="g.id">{{ g.name }}</option>
          </SelectInput>
        </label>
      </div>
      <p class="text-xs text-(--text-muted)">{{ t('setup.steps.invites.expandedCount', {n: expandedBulk.length}) }}</p>
    </div>

    <div v-else-if="tab === 'csv'" class="space-y-3">
      <p class="text-sm">{{ t('setup.steps.invites.csvIntro') }}</p>
      <p class="text-sm text-(--text-muted)">{{ t('setup.steps.invites.csvHandoffNote') }}</p>
      <div>
        <PrimaryButton @click="openMemberImport">{{ t('setup.steps.invites.openMemberImport') }}</PrimaryButton>
      </div>
    </div>

    <div v-else class="space-y-4">
      <div v-for="(row, idx) in richRows" :key="idx" class="border border-(--border) rounded p-3 space-y-2">
        <div class="flex flex-wrap gap-2">
          <TextInput v-model="row.firstName" :placeholder="t('setup.steps.invites.firstName')" class="flex-1"/>
          <TextInput v-model="row.lastName" :placeholder="t('setup.steps.invites.lastName')" class="flex-1"/>
          <TextInput v-model="row.email" :placeholder="t('setup.steps.invites.email')" type="email" class="flex-1"/>
          <DeleteButton :title="t('setup.actions.removeRow')" @click="removeRichRow(idx)"/>
        </div>
        <div class="flex flex-wrap gap-2">
          <label class="block text-xs flex-1">
            {{ t('setup.steps.invites.userType') }}
            <SelectInput v-model="row.userType">
              <option v-for="ut in USER_TYPES" :key="ut" :value="ut">{{ ut }}</option>
            </SelectInput>
          </label>
          <label class="block text-xs flex-1">
            {{ t('setup.steps.invites.group') }}
            <SelectInput v-model="row.groupId">
              <option :value="null">{{ t('setup.steps.invites.groupNone') }}</option>
              <option v-for="g in groups" :key="g.id" :value="g.id">{{ g.name }}</option>
            </SelectInput>
          </label>
        </div>
        <details v-if="row.userType === 'MEMBER'">
          <summary class="text-xs cursor-pointer">
            {{ t('setup.steps.invites.guardiansToggle', {n: row.guardians.length}) }}
          </summary>
          <div class="mt-2 space-y-2">
            <div v-for="(g, gIdx) in row.guardians" :key="gIdx" class="flex flex-wrap gap-2">
              <TextInput v-model="g.firstName" :placeholder="t('setup.steps.invites.firstName')" class="flex-1"/>
              <TextInput v-model="g.lastName" :placeholder="t('setup.steps.invites.lastName')" class="flex-1"/>
              <TextInput v-model="g.email" :placeholder="t('setup.steps.invites.email')" type="email" class="flex-1"/>
              <DeleteButton :title="t('setup.actions.removeRow')" @click="removeGuardian(idx, gIdx)"/>
            </div>
            <SecondaryButton @click="addGuardian(idx)">{{ t('setup.steps.invites.addGuardian') }}</SecondaryButton>
          </div>
        </details>
      </div>
      <SecondaryButton @click="addRichRow">{{ t('setup.actions.addRow') }}</SecondaryButton>
    </div>
  </SetupLayout>
</template>
