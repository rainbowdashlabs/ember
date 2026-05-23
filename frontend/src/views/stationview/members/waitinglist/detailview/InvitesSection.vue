/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import type { WaitingListInvite } from '@/api/types'

defineProps<{
  invites: WaitingListInvite[]
}>()

const emit = defineEmits<{
  createInvite: []
  deleteInvite: [inviteId: number]
  copyLink: [code: string]
}>()

const { t } = useI18n()

function formatDate(dateStr: string | undefined | null): string {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleDateString()
}

function formatDateTime(dateStr: string | undefined | null): string {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleString()
}
</script>

<template>
  <NeutralContainer class="space-y-4">
    <div class="flex items-center justify-between">
      <SubHeader>{{ t('waitingList.invites') }}</SubHeader>
      <PrimaryButton @click="emit('createInvite')">
        <font-awesome-icon :icon="['fas', 'plus']" class="mr-2" />
        {{ t('waitingList.createInvite') }}
      </PrimaryButton>
    </div>

    <div v-if="invites.length === 0" class="text-center text-(--text-muted) py-4">
      {{ t('waitingList.noInvites') }}
    </div>

    <div class="space-y-2">
      <div
        v-for="invite in invites"
        :key="invite.id"
        class="flex items-center justify-between gap-4 rounded-lg px-4 py-3 bg-bg-light-accent/30 dark:bg-bg-dark-accent/30"
      >
        <div class="flex-1 min-w-0 space-y-1">
          <div class="flex items-center gap-2 flex-wrap">
            <code class="text-sm font-mono bg-bg-light-accent dark:bg-bg-dark-accent px-2 py-0.5 rounded select-all">{{ invite.code }}</code>
            <IconButton icon="copy" :label="t('waitingList.copyLink')" @click="emit('copyLink', invite.code)" />
          </div>
          <div class="text-xs text-(--text-muted) flex flex-wrap gap-3">
            <span>{{ t('waitingList.uses') }}: {{ invite.uses }}{{ invite.maxUses ? ' / ' + invite.maxUses : '' }}</span>
            <span v-if="invite.expiresAt">{{ t('waitingList.expiresAt') }}: {{ formatDateTime(invite.expiresAt) }}</span>
            <span>{{ t('waitingList.createdAt') }}: {{ formatDate(invite.createdAt) }}</span>
          </div>
        </div>
        <DeleteButton @click="emit('deleteInvite', invite.id)" />
      </div>
    </div>
  </NeutralContainer>
</template>
