/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import type {DemoAccount, RoleGroup} from '@/views/loginview/demoTypes'

defineProps<{
  roleGroups: RoleGroup[]
  loading: boolean
  compact?: boolean
  roleLabel: (a: DemoAccount) => string
}>()

const emit = defineEmits<{
  login: [account: DemoAccount]
}>()

const {t} = useI18n()
</script>

<template>
  <div v-for="group in roleGroups" :key="group.label" :class="compact ? 'mb-3' : 'space-y-2'">
    <p v-if="compact" class="text-xs font-semibold text-(--text-muted) mb-1">{{ group.label }}</p>
    <SectionHeader v-else>{{ group.label }}</SectionHeader>
    <div
        :class="compact
          ? 'grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-1.5'
          : 'grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-2'"
    >
      <NeutralContainer
          v-for="account in group.accounts"
          :key="account.email"
          :class="[
            { 'opacity-50 pointer-events-none': loading },
            compact
              ? 'cursor-pointer hover:border-primary transition-colors py-1.5 px-2.5'
              : 'cursor-pointer hover:border-primary transition-colors py-2 px-3',
          ]"
          @click="emit('login', account)"
      >
        <div :class="compact ? 'font-medium text-xs' : 'font-medium text-sm'">
          {{ account.firstName }} {{ account.lastName }}
          <ErrorBadge v-if="!account.profileComplete" :class="compact ? 'ml-1 text-[9px]' : 'ml-1 text-[10px]'">
            {{ t('login.incomplete') }}
          </ErrorBadge>
        </div>
        <div :class="compact ? 'text-[10px] text-(--text-muted)' : 'text-xs text-(--text-muted)'">
          {{ roleLabel(account) }}
        </div>
        <div v-if="account.groups.length > 0"
             :class="compact ? 'flex flex-wrap gap-0.5 mt-0.5' : 'flex flex-wrap gap-1 mt-1'">
          <span v-for="g in account.groups" :key="g"
                :class="compact
                  ? 'inline-block rounded-full px-1 text-[9px] bg-secondary/15 text-secondary-accent'
                  : 'inline-block rounded-full px-1.5 py-0 text-[10px] bg-secondary/15 text-secondary-accent'">
            {{ g }}
          </span>
        </div>
        <div v-if="account.tags.length > 0"
             :class="compact ? 'flex flex-wrap gap-0.5 mt-0.5' : 'flex flex-wrap gap-1 mt-0.5'">
          <span v-for="tag in account.tags" :key="tag"
                :class="compact
                  ? 'inline-block rounded-full px-1 text-[9px] bg-primary/15 text-primary'
                  : 'inline-block rounded-full px-1.5 py-0 text-[10px] bg-primary/15 text-primary'">
            {{ tag }}
          </span>
        </div>
      </NeutralContainer>
    </div>
  </div>
</template>
