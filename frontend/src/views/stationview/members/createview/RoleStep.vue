/*
*     SPDX-License-Identifier: AGPL-3.0-only
*
*     Copyright (C) RainbowDashLabs and Contributor
*/
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'

const {t} = useI18n()

defineProps<{
  modelValue: 'MEMBER' | 'MEMBER_MANAGER' | 'TEAM'
}>()

const emit = defineEmits<{
  'update:modelValue': [value: 'MEMBER' | 'MEMBER_MANAGER' | 'TEAM']
  next: []
}>()
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SectionHeader>{{ t('membersCreate.stepRole') }}</SectionHeader>
    <p class="text-sm text-(--text-muted)">{{ t('membersCreate.stepRoleHint') }}</p>

    <div class="grid gap-3 sm:grid-cols-3">
      <NeutralContainer
          :class="modelValue === 'MEMBER' ? 'border-primary ring-2 ring-primary/30 bg-primary/10 scale-105' : 'hover:border-primary hover:scale-[1.02]'"
          class="cursor-pointer text-center py-6 transition-all"
          @click="emit('update:modelValue', 'MEMBER')"
      >
        <font-awesome-icon :icon="['fas', 'user']" class="text-2xl mb-2"/>
        <div class="font-medium">{{ t('membersCreate.roleMember') }}</div>
        <p class="text-xs text-(--text-muted) mt-1">{{ t('membersCreate.roleMemberHint') }}</p>
      </NeutralContainer>

      <NeutralContainer
          :class="modelValue === 'MEMBER_MANAGER' ? 'border-primary ring-2 ring-primary/30 bg-primary/10 scale-105' : 'hover:border-primary hover:scale-[1.02]'"
          class="cursor-pointer text-center py-6 transition-all"
          @click="emit('update:modelValue', 'MEMBER_MANAGER')"
      >
        <font-awesome-icon :icon="['fas', 'user-plus']" class="text-2xl mb-2"/>
        <div class="font-medium">{{ t('membersCreate.roleMemberManager') }}</div>
        <p class="text-xs text-(--text-muted) mt-1">{{ t('membersCreate.roleMemberManagerHint') }}</p>
      </NeutralContainer>

      <NeutralContainer
          :class="modelValue === 'TEAM' ? 'border-primary ring-2 ring-primary/30 bg-primary/10 scale-105' : 'hover:border-primary hover:scale-[1.02]'"
          class="cursor-pointer text-center py-6 transition-all"
          @click="emit('update:modelValue', 'TEAM')"
      >
        <font-awesome-icon :icon="['fas', 'users']" class="text-2xl mb-2"/>
        <div class="font-medium">{{ t('membersCreate.roleTeam') }}</div>
        <p class="text-xs text-(--text-muted) mt-1">{{ t('membersCreate.roleTeamHint') }}</p>
      </NeutralContainer>
    </div>

    <div class="flex justify-end">
      <PrimaryButton @click="emit('next')">{{ t('membersCreate.next') }}</PrimaryButton>
    </div>
  </NeutralContainer>
</template>
