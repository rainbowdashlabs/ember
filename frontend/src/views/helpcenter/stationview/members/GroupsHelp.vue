/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import HelpTip from '@/components/helpcenter/HelpTip.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import IconButton from '@/components/button/IconButton.vue'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.membersGroups.title')" :subtitle="t('helpCenter.membersGroups.subtitle')">
    <HelpSection :title="t('helpCenter.membersGroups.whatIs')">
      <p>{{ t('helpCenter.membersGroups.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.membersGroups.createTitle')">
      <p>{{ t('helpCenter.membersGroups.create') }}</p>
      <p>{{ t('helpCenter.membersGroups.select') }}</p>
      <p>{{ t('helpCenter.membersGroups.addMember') }}</p>
    </HelpSection>

    <!-- Dummy: Groups list + panel -->
    <div class="grid gap-6 lg:grid-cols-2">
      <!-- Groups list -->
      <div class="space-y-4">
        <div class="flex items-center justify-between">
          <SectionHeader>{{ t('memberGroups.title') }}</SectionHeader>
          <PrimaryButton>
            <font-awesome-icon :icon="['fas', 'plus']" class="mr-2"/>
            {{ t('memberGroups.create') }}
          </PrimaryButton>
        </div>
        <div class="space-y-2">
          <NeutralContainer class="flex items-center justify-between cursor-pointer border-primary">
            <span class="font-medium">Anfänger</span>
            <div class="flex items-center gap-2">
              <EditButton/>
              <DeleteButton/>
            </div>
          </NeutralContainer>
          <NeutralContainer class="flex items-center justify-between cursor-pointer hover:border-primary">
            <span class="font-medium">Fortgeschrittene</span>
            <div class="flex items-center gap-2">
              <EditButton/>
              <DeleteButton/>
            </div>
          </NeutralContainer>
          <NeutralContainer class="flex items-center justify-between cursor-pointer hover:border-primary">
            <span class="font-medium">Betreuer</span>
            <div class="flex items-center gap-2">
              <EditButton/>
              <DeleteButton/>
            </div>
          </NeutralContainer>
        </div>
      </div>

      <!-- Group members panel -->
      <div class="space-y-4">
        <SectionHeader>Anfänger</SectionHeader>
        <div class="space-y-1">
          <label class="block text-sm font-medium text-(--text-muted)">{{ t('memberGroups.currentMembers') }}</label>
          <div class="space-y-1">
            <div class="flex items-center justify-between rounded-lg px-3 py-2 bg-bg-light-accent dark:bg-bg-dark-accent">
              <span class="text-sm font-medium">Max Mustermann</span>
              <IconButton :icon="['fas', 'xmark']" label="Remove" class="text-error hover:text-error/80" />
            </div>
            <div class="flex items-center justify-between rounded-lg px-3 py-2 bg-bg-light-accent dark:bg-bg-dark-accent">
              <span class="text-sm font-medium">Anna Schmidt</span>
              <IconButton :icon="['fas', 'xmark']" label="Remove" class="text-error hover:text-error/80" />
            </div>
          </div>
        </div>
        <div class="space-y-1">
          <label class="block text-sm font-medium text-(--text-muted)">{{ t('memberGroups.addMembers') }}</label>
          <div class="space-y-1">
            <div class="flex items-center justify-between rounded-lg px-3 py-2 hover:bg-bg-light-accent dark:hover:bg-bg-dark-accent cursor-pointer">
              <span class="text-sm font-medium">Lisa Weber</span>
              <font-awesome-icon :icon="['fas', 'plus']" class="text-primary text-sm"/>
            </div>
          </div>
        </div>
      </div>
    </div>

    <HelpSection :title="t('helpCenter.membersGroups.rolesTitle')">
      <p>{{ t('helpCenter.membersGroups.rolesText') }}</p>
    </HelpSection>

    <!-- Dummy: Group roles -->
    <NeutralContainer class="space-y-2">
      <label class="block text-sm font-medium text-(--text-muted)">{{ t('memberGroups.roles') }}</label>
      <div class="flex flex-wrap gap-2">
        <SelectionToggleButton :selected="true">
          Mitglied
        </SelectionToggleButton>
        <SelectionToggleButton :selected="true">
          Login
        </SelectionToggleButton>
        <SelectionToggleButton :selected="false">
          Team
        </SelectionToggleButton>
      </div>
    </NeutralContainer>

    <HelpTip>{{ t('helpCenter.membersGroups.tip') }}</HelpTip>
  </HelpArticle>
</template>
