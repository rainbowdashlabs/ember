/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SearchInput from '@/components/input/text/SearchInput.vue'
import MutedText from '@/components/typography/MutedText.vue'
import DemoAccountGroups from '@/views/loginview/DemoAccountGroups.vue'
import DemoStationPicker from '@/views/loginview/DemoStationPicker.vue'
import type {DemoAccount, DemoAccountsView} from '@/composables/useDemoAccounts'

/**
 * The whole of choosing a demo account: a search across every station, and the picked station's
 * people in role bands underneath it.
 *
 * One component rather than two, because the demo login page and the dev footer offer exactly the
 * same thing at two sizes.
 */
defineProps<{
  view: DemoAccountsView
  loading: boolean
  compact?: boolean
}>()

const emit = defineEmits<{
  (e: 'login', account: DemoAccount): void
}>()

const activeStation = defineModel<string>('activeStation', {required: true})
const search = defineModel<string>('search', {required: true})

const {t} = useI18n()
</script>

<template>
  <div :class="compact ? 'space-y-3' : 'space-y-4'">
    <SearchInput v-model="search" :placeholder="t('demo.searchPlaceholder')"/>

    <div v-if="view.searching" data-testid="demo-search-results">
      <DemoAccountGroups v-if="view.searchGroups.length > 0"
                         :role-groups="view.searchGroups" :loading="loading" :compact="compact"
                         @login="a => emit('login', a)"/>
      <MutedText v-else tag="p" size="sm">{{ t('demo.searchEmpty') }}</MutedText>
    </div>

    <template v-else>
      <DemoAccountGroups v-if="view.noStationRoleGroups.length > 0"
                         :role-groups="view.noStationRoleGroups" :loading="loading" :compact="compact"
                         @login="a => emit('login', a)"/>

      <div v-if="view.clusterRoleGroups.length > 0">
        <MutedText tag="p" class="mb-1">{{ t('demo.clusterLoginHint') }}</MutedText>
        <DemoAccountGroups :role-groups="view.clusterRoleGroups" :loading="loading" :compact="compact"
                           @login="a => emit('login', a)"/>
      </div>

      <DemoStationPicker v-if="view.showStationPicker" v-model="activeStation"
                         :choices="view.stationChoices" :compact="compact"/>

      <DemoAccountGroups :role-groups="view.roleGroups" :loading="loading" :compact="compact"
                         @login="a => emit('login', a)"/>
    </template>
  </div>
</template>
