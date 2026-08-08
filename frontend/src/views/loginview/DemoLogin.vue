/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import Alert from '@/components/feedback/Alert.vue'
import PageHeader from '@/components/typography/PageHeader.vue'
import PageHeroIcon from '@/components/typography/PageHeroIcon.vue'
import MutedText from '@/components/typography/MutedText.vue'
import TabBar from '@/components/navigation/TabBar.vue'
import DemoAccountGroups from '@/views/loginview/DemoAccountGroups.vue'
import type {DemoAccount, RoleGroup, StationTab} from '@/composables/useDemoAccounts'

const props = defineProps<{
  error: string
  loading: boolean
  noStationRoleGroups: RoleGroup[]
  roleGroups: RoleGroup[]
  stationTabs: StationTab[]
  showStationTabs: boolean
  roleLabel: (a: DemoAccount) => string
}>()

const emit = defineEmits<{
  (e: 'login', account: DemoAccount): void
}>()

const activeStationTab = defineModel<string>('activeStationTab', {required: true})

const {t} = useI18n()
</script>

<template>
  <div class="text-center">
    <PageHeroIcon :icon="['fas', 'fire']"/>
    <PageHeader class="text-2xl font-bold">{{ t('demo.title') }}</PageHeader>
    <MutedText tag="p" size="sm" class="mt-1">{{ t('demo.loginHint') }}</MutedText>
  </div>
  <Alert v-if="props.error" variant="error">{{ props.error }}</Alert>

  <DemoAccountGroups v-if="props.noStationRoleGroups.length > 0"
                     :role-groups="props.noStationRoleGroups" :loading="props.loading"
                     :role-label="props.roleLabel" @login="a => emit('login', a)"/>

  <TabBar v-if="props.showStationTabs" v-model="activeStationTab" :tabs="props.stationTabs"/>

  <DemoAccountGroups :role-groups="props.roleGroups" :loading="props.loading"
                     :role-label="props.roleLabel" @login="a => emit('login', a)"/>
</template>
