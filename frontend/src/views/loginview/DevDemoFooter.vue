/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import DemoAccountBrowser from '@/views/loginview/DemoAccountBrowser.vue'
import type {DemoAccount, DemoAccountsView} from '@/composables/useDemoAccounts'

const props = defineProps<{
  view: DemoAccountsView
  loading: boolean
}>()

const emit = defineEmits<{
  (e: 'login', account: DemoAccount): void
}>()

const activeStation = defineModel<string>('activeStation', {required: true})
const search = defineModel<string>('search', {required: true})

const {t} = useI18n()
</script>

<template>
  <div class="border-t border-bg-light-accent dark:border-bg-dark-accent pt-4 mt-2">
    <p class="text-sm font-medium mb-3">{{ t('demo.devLoginHint') }}</p>

    <DemoAccountBrowser v-model:active-station="activeStation" v-model:search="search"
                        compact :view="props.view" :loading="props.loading"
                        @login="a => emit('login', a)"/>
  </div>
</template>
