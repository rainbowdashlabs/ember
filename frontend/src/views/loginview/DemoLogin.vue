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
import DemoAccountBrowser from '@/views/loginview/DemoAccountBrowser.vue'
import type {DemoAccount, DemoAccountsView} from '@/composables/useDemoAccounts'

const props = defineProps<{
  view: DemoAccountsView
  error: string
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
  <div class="text-center">
    <PageHeroIcon :icon="['fas', 'fire']"/>
    <PageHeader class="text-2xl font-bold">{{ t('demo.title') }}</PageHeader>
    <MutedText tag="p" size="sm" class="mt-1">{{ t('demo.loginHint') }}</MutedText>
  </div>
  <Alert v-if="props.error" variant="error">{{ props.error }}</Alert>

  <DemoAccountBrowser v-model:active-station="activeStation" v-model:search="search"
                      :view="props.view" :loading="props.loading" @login="a => emit('login', a)"/>
</template>
