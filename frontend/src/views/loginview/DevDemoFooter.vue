/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import TabBar from '@/components/navigation/TabBar.vue'
import DemoAccountGroups from '@/views/loginview/DemoAccountGroups.vue'
import type {DemoAccount, RoleGroup, StationTab} from '@/composables/useDemoAccounts'

const props = defineProps<{
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
  <div class="border-t border-bg-light-accent dark:border-bg-dark-accent pt-4 mt-2">
    <p class="text-sm font-medium mb-3">{{ t('demo.devLoginHint') }}</p>
    <DemoAccountGroups v-if="props.noStationRoleGroups.length > 0"
                       :role-groups="props.noStationRoleGroups" :loading="props.loading"
                       :role-label="props.roleLabel" compact @login="a => emit('login', a)" class="mb-3"/>
    <TabBar v-if="props.showStationTabs" v-model="activeStationTab" :tabs="props.stationTabs" class="mb-3"/>
    <DemoAccountGroups :role-groups="props.roleGroups" :loading="props.loading"
                       :role-label="props.roleLabel" compact @login="a => emit('login', a)"/>
  </div>
</template>
