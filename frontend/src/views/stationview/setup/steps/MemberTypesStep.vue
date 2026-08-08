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
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import PermissionPicker from '@/components/input/PermissionPicker.vue'
import {stationMembers} from '@/api'
import {StationUserType, type PermissionGrant} from '@/api/types'
import {useSetupStatus} from '@/composables/useSetupStatus'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {goToNextStep} from '@/views/stationview/setup/steps'
import {apiErrorMessage} from '@/util/apiError'

const {t} = useI18n()
const router = useRouter()
const {reload} = useSetupStatus()

/**
 * Permissions granted by the {@link StationUserType} enum itself (backend
 * StationUserType.defaultPermissions()). These are inherent to the user type and cannot be
 * removed — the picker shows them as locked.
 */
const USER_TYPE_BUILTIN_DEFAULTS: Record<string, string[]> = {
    TRIAL: [],
    MEMBER: ['USER'],
    GUARDIAN: ['LOGIN', 'MEMBER_GUARDIAN'],
    TEAM: ['LOGIN'],
    MANAGER: ['STATION_ADMINISTRATOR', 'LOGIN'],
}

const USER_TYPES = [
    {value: StationUserType.TRIAL, label: 'Probe', desc: 'Probemitglieder ohne Standardberechtigungen.'},
    {value: StationUserType.MEMBER, label: 'Mitglied', desc: 'Standardmitglieder der Station.'},
    {value: StationUserType.GUARDIAN, label: 'Erziehungsberechtigter', desc: 'Verwalten zugeordnete Mitglieder.'},
    {value: StationUserType.TEAM, label: 'Team', desc: 'Betreuer und Teamer der Station.'},
    {value: StationUserType.MANAGER, label: 'Leitung', desc: 'Leitungsmitglieder mit Verwaltungsrechten.'},
] as const

const allRoles = ref<PermissionGrant[]>([])
const permissionCache = reactive<Record<string, Set<number>>>({})
const selectedType = ref<string>(StationUserType.MEMBER)
const loading = ref(true)
const error = ref('')

const selectedIds = computed<Set<number>>({
    get: () => permissionCache[selectedType.value] ?? new Set<number>(),
    set: (newIds) => {
        permissionCache[selectedType.value] = newIds
    },
})

const lockedNames = computed<Map<string, string>>(
    () => new Map((USER_TYPE_BUILTIN_DEFAULTS[selectedType.value] ?? []).map((n) => [n, t('permissions.lockedByUserType')])),
)

onMounted(async () => {
    try {
        allRoles.value = await stationMembers.listAllPermissions()
        await Promise.all(
            USER_TYPES.map(async (ut) => {
                const grants = await stationMembers.getUserTypePermissions(ut.value)
                permissionCache[ut.value] = new Set(grants.map((g) => g.id))
            }),
        )
    } catch {
        error.value = t('common.error')
    } finally {
        loading.value = false
    }
})

function selectType(userType: string) {
    selectedType.value = userType
}

async function onPermissionChange(newIds: Set<number>) {
    const userType = selectedType.value
    permissionCache[userType] = newIds
    try {
        await stationMembers.setUserTypePermissions(userType, [...newIds])
    } catch (e: unknown) {
        error.value = apiErrorMessage(e) || t('common.error')
    }
}

const {running: saving, run: proceed} = useAsyncAction(async () => {
    await reload()
    goToNextStep(router, 'member-types')
})
</script>

<template>
  <SetupLayout step-id="member-types" :saving="saving" @save="proceed">
    <Alert v-if="error" variant="error">{{ error }}</Alert>
    <Spinner v-if="loading" size="lg"/>

    <div v-else class="grid gap-6 lg:grid-cols-2">
      <div class="space-y-4">
        <SectionHeader>{{ t('userTypePermissions.title') }}</SectionHeader>
        <MutedText size="sm">{{ t('userTypePermissions.description') }}</MutedText>

        <div class="space-y-2">
          <NeutralContainer
              v-for="ut in USER_TYPES"
              :key="ut.value"
              :class="selectedType === ut.value ? 'border-primary' : 'hover:border-primary'"
              class="cursor-pointer transition-colors"
              @click="selectType(ut.value)"
          >
            <div class="font-medium">{{ ut.label }}</div>
            <MutedText size="sm">{{ ut.desc }}</MutedText>
          </NeutralContainer>
        </div>
      </div>

      <div class="space-y-4">
        <SectionHeader>{{ USER_TYPES.find((ut) => ut.value === selectedType)?.label }}</SectionHeader>
        <PermissionPicker
            :model-value="selectedIds"
            :all-roles="allRoles"
            :locked-permissions="lockedNames"
            @update:model-value="onPermissionChange"
        />
      </div>
    </div>
  </SetupLayout>
</template>
