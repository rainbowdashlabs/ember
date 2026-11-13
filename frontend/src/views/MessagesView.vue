/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import MessagesWorkspace from '@/views/messagesview/MessagesWorkspace.vue'
import {useSession} from '@/composables/useSession'

/**
 * Messages between people, outside any station. While messages are turned off for the instance the
 * page only says so and asks the server for nothing.
 */
const {t} = useI18n()
const {sessionInfo} = useSession()
const messagesOn = computed(() => sessionInfo.value?.messagesEnabled !== false)
</script>

<template>
  <ViewContent :title="t('pages.messages.title')" :subtitle="t('pages.messages.subtitle')">
    <MessagesWorkspace v-if="messagesOn"/>
    <EmptyState v-else :message="t('messages.offForInstance')"/>
  </ViewContent>
</template>
