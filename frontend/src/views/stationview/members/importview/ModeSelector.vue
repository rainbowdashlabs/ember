/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import RowLink from '@/components/navigation/RowLink.vue'

const { t } = useI18n()
</script>

<template>
  <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
    <NeutralContainer class="space-y-2 border-primary ring-2 ring-primary/30">
      <div class="flex items-center gap-2">
        <font-awesome-icon :icon="['fas', 'users']" class="text-primary" />
        <span class="font-semibold">{{ t('memberImport.modeMembers') }}</span>
      </div>
      <p class="text-sm text-(--text-muted)">{{ t('memberImport.modeMembersHint') }}</p>
    </NeutralContainer>
    <RowLink :to="{ name: 'members-import-team' }" class="h-full">
      <NeutralContainer clickable class="h-full space-y-2">
        <div class="flex items-center gap-2">
          <font-awesome-icon :icon="['fas', 'user-shield']" class="text-primary" />
          <span class="font-semibold">{{ t('teamImport.modeTeam') }}</span>
        </div>
        <p class="text-sm text-(--text-muted)">{{ t('teamImport.modeTeamHint') }}</p>
      </NeutralContainer>
    </RowLink>
  </div>
</template>
