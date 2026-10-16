/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {onMounted} from 'vue'
import {useI18n} from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'
import CountBadge from '@/components/badge/CountBadge.vue'
import {useMessagesStatus} from '@/composables/useMessagesStatus'
import {useMessageStream} from '@/composables/useMessageStream'

/**
 * The header's way into messages, with the number of unread messages beside it. It is the same in
 * every place, because messages belong to the person and not to the place they are in, and it is
 * not there at all for somebody whose guardian has not turned messages on.
 *
 * <p>The tab's message stream is opened here, since the header is on every signed-in page; while it
 * does not get through, the count is asked for once a minute.
 */
const {t} = useI18n()
const {status, refresh} = useMessagesStatus()

onMounted(refresh)
useMessageStream(() => { void refresh() }, 60_000, () => { void refresh() })
</script>

<template>
  <div v-if="status.available" class="relative">
    <IconButton
        :icon="['fas', 'comments']"
        :label="status.unread > 0 ? t('messages.openUnread', {count: status.unread}) : t('messages.open')"
        to="/messages"
        data-testid="messages-button"
        class="text-[var(--text)] hover:bg-bg-light-accent dark:hover:bg-bg-dark-accent"
    />
    <CountBadge v-if="status.unread > 0" :count="status.unread" small
                class="pointer-events-none absolute -top-1 -right-1"/>
  </div>
</template>
