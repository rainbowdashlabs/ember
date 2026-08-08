/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import PermissionPicker from '@/components/input/PermissionPicker.vue'
import {StationUserType, type PermissionGrant} from '@/api/types'
import {stationMembers} from '@/api'
import {useConfigPanel} from '@/composables/useConfigPanel'
import {apiErrorMessage} from '@/util/apiError'

const {t} = useI18n()

const {config: allRoles, loading, error} = useConfigPanel<PermissionGrant[]>({
  initial: [],
  fetch: () => stationMembers.listAllPermissions(),
})

const selectedType = ref<string | null>(null)
const typePermissions = ref<PermissionGrant[]>([])
const typeLoading = ref(false)

const USER_TYPES = [
  {value: StationUserType.TRIAL, label: 'Probe', desc: 'Probemitglieder ohne Standardberechtigungen.'},
  {value: StationUserType.MEMBER, label: 'Mitglied', desc: 'Standardmitglieder der Station.'},
  {value: StationUserType.GUARDIAN, label: 'Erziehungsberechtigter', desc: 'Verwalten zugeordnete Mitglieder.'},
  {value: StationUserType.TEAM, label: 'Team', desc: 'Betreuer und Teamer der Station.'},
  {value: StationUserType.MANAGER, label: 'Leitung', desc: 'Leitungsmitglieder mit Verwaltungsrechten.'},
] as const

const permissionIds = computed({
  get: () => new Set(typePermissions.value.map(r => r.id)),
  set: (newIds: Set<number>) => syncPermissions(newIds),
})

async function selectType(userType: string) {
  selectedType.value = userType
  typeLoading.value = true
  try {
    typePermissions.value = await stationMembers.getUserTypePermissions(userType)
  } catch {
    error.value = t('common.error')
    typePermissions.value = []
  } finally {
    typeLoading.value = false
  }
}

async function syncPermissions(newIds: Set<number>) {
  if (!selectedType.value) return
  try {
    typePermissions.value = await stationMembers.setUserTypePermissions(selectedType.value, [...newIds])
  } catch (e: unknown) {
    error.value = apiErrorMessage(e) || t('common.error')
  }
}
</script>

<template>
  <ViewContent
      :title="t('pages.members-type-permissions.title')"
      :subtitle="t('pages.members-type-permissions.subtitle')"
  >
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <div v-if="!loading" class="grid gap-6 lg:grid-cols-2">
        <div class="space-y-4">
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

        <!-- Permission picker -->
        <div v-if="selectedType" class="space-y-4">
          <SectionHeader>{{ USER_TYPES.find(ut => ut.value === selectedType)?.label }}</SectionHeader>

          <Spinner v-if="typeLoading" size="md"/>

          <template v-if="!typeLoading">
            <PermissionPicker v-model="permissionIds" :all-roles="allRoles"/>
            <MutedText v-if="typePermissions.length === 0" size="sm">{{ t('userTypePermissions.noPermissions') }}</MutedText>
          </template>
        </div>

        <div v-else class="flex items-center justify-center text-(--text-muted) py-12">
          {{ t('userTypePermissions.selectHint') }}
        </div>
      </div>
    </div>
  </ViewContent>
</template>
