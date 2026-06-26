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
import {StationUserType} from '@/api/types'
import MutedText from '@/components/typography/MutedText.vue'

const {t} = useI18n()

const modelValue = defineModel<'TRIAL' | 'MEMBER' | 'GUARDIAN' | 'TEAM'>({required: true})

const emit = defineEmits<{
  next: []
}>()
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SectionHeader>{{ t('membersCreate.stepRole') }}</SectionHeader>
    <p class="text-sm text-(--text-muted)">{{ t('membersCreate.stepRoleHint') }}</p>

    <div class="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
      <NeutralContainer
          :class="modelValue === StationUserType.TRIAL ? 'border-primary ring-2 ring-primary/30 bg-primary/10 scale-105' : 'hover:border-primary hover:scale-[1.02]'"
          class="cursor-pointer text-center py-6 transition-all"
          @click="modelValue = StationUserType.TRIAL"
      >
        <font-awesome-icon :icon="['fas', 'user-clock']" class="text-2xl mb-2"/>
        <div class="font-medium">{{ t('membersCreate.roleTrial') }}</div>
        <MutedText tag="p" class="mt-1">{{ t('membersCreate.roleTrialHint') }}</MutedText>
      </NeutralContainer>

      <NeutralContainer
          :class="modelValue === StationUserType.MEMBER ? 'border-primary ring-2 ring-primary/30 bg-primary/10 scale-105' : 'hover:border-primary hover:scale-[1.02]'"
          class="cursor-pointer text-center py-6 transition-all"
          @click="modelValue = StationUserType.MEMBER"
      >
        <font-awesome-icon :icon="['fas', 'user']" class="text-2xl mb-2"/>
        <div class="font-medium">{{ t('membersCreate.roleMember') }}</div>
        <MutedText tag="p" class="mt-1">{{ t('membersCreate.roleMemberHint') }}</MutedText>
      </NeutralContainer>

      <NeutralContainer
          :class="modelValue === StationUserType.GUARDIAN ? 'border-primary ring-2 ring-primary/30 bg-primary/10 scale-105' : 'hover:border-primary hover:scale-[1.02]'"
          class="cursor-pointer text-center py-6 transition-all"
          @click="modelValue = StationUserType.GUARDIAN"
      >
        <font-awesome-icon :icon="['fas', 'user-plus']" class="text-2xl mb-2"/>
        <div class="font-medium">{{ t('membersCreate.roleMemberManager') }}</div>
        <MutedText tag="p" class="mt-1">{{ t('membersCreate.roleMemberManagerHint') }}</MutedText>
      </NeutralContainer>

      <NeutralContainer
          :class="modelValue === StationUserType.TEAM ? 'border-primary ring-2 ring-primary/30 bg-primary/10 scale-105' : 'hover:border-primary hover:scale-[1.02]'"
          class="cursor-pointer text-center py-6 transition-all"
          @click="modelValue = StationUserType.TEAM"
      >
        <font-awesome-icon :icon="['fas', 'users']" class="text-2xl mb-2"/>
        <div class="font-medium">{{ t('membersCreate.roleTeam') }}</div>
        <MutedText tag="p" class="mt-1">{{ t('membersCreate.roleTeamHint') }}</MutedText>
      </NeutralContainer>
    </div>

    <div class="flex justify-end">
      <PrimaryButton @click="emit('next')">{{ t('membersCreate.next') }}</PrimaryButton>
    </div>
  </NeutralContainer>
</template>
