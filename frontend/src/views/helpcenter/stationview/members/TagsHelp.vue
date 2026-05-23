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
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import IconButton from '@/components/button/IconButton.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.membersTags.title')" :subtitle="t('helpCenter.membersTags.subtitle')">
    <HelpSection :title="t('helpCenter.membersTags.whatIs')">
      <p>{{ t('helpCenter.membersTags.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.membersTags.differenceTitle')">
      <p>{{ t('helpCenter.membersTags.differenceText') }}</p>
    </HelpSection>

    <!-- Dummy: Tags list + panel -->
    <div class="grid gap-6 lg:grid-cols-2">
      <!-- Tags list -->
      <div class="space-y-4">
        <div class="flex items-center justify-between">
          <SectionHeader>Tags</SectionHeader>
          <PrimaryButton>
            <font-awesome-icon :icon="['fas', 'plus']" class="mr-2"/>
            Tag erstellen
          </PrimaryButton>
        </div>
        <div class="space-y-2">
          <NeutralContainer class="flex items-center justify-between cursor-pointer border-primary">
            <span class="font-medium">Ersthelfer</span>
            <div class="flex items-center gap-2">
              <SecondaryButton>
                <font-awesome-icon :icon="['fas', 'people-group']" class="mr-1"/>
                Zu Gruppe
              </SecondaryButton>
              <EditButton/>
              <DeleteButton/>
            </div>
          </NeutralContainer>
          <NeutralContainer class="flex items-center justify-between cursor-pointer hover:border-primary">
            <span class="font-medium">Fahrer</span>
            <div class="flex items-center gap-2">
              <SecondaryButton>
                <font-awesome-icon :icon="['fas', 'people-group']" class="mr-1"/>
                Zu Gruppe
              </SecondaryButton>
              <EditButton/>
              <DeleteButton/>
            </div>
          </NeutralContainer>
        </div>
      </div>

      <!-- Tag members panel -->
      <div class="space-y-4">
        <SectionHeader>Ersthelfer</SectionHeader>
        <div class="space-y-1">
          <label class="block text-sm font-medium text-(--text-muted)">Mitglieder</label>
          <div class="space-y-1">
            <div class="flex items-center justify-between rounded-lg px-3 py-2 bg-bg-light-accent dark:bg-bg-dark-accent">
              <span class="text-sm font-medium">Max Mustermann</span>
              <IconButton :icon="['fas', 'xmark']" label="Remove" class="text-error hover:text-error/80" />
            </div>
          </div>
        </div>
        <div class="space-y-1">
          <label class="block text-sm font-medium text-(--text-muted)">Mitglied hinzufügen</label>
          <div class="space-y-1">
            <div class="flex items-center justify-between rounded-lg px-3 py-2 hover:bg-bg-light-accent dark:hover:bg-bg-dark-accent cursor-pointer">
              <span class="text-sm font-medium">Anna Schmidt</span>
              <font-awesome-icon :icon="['fas', 'plus']" class="text-primary text-sm"/>
            </div>
            <div class="flex items-center justify-between rounded-lg px-3 py-2 hover:bg-bg-light-accent dark:hover:bg-bg-dark-accent cursor-pointer">
              <span class="text-sm font-medium">Lisa Weber</span>
              <font-awesome-icon :icon="['fas', 'plus']" class="text-primary text-sm"/>
            </div>
          </div>
        </div>
      </div>
    </div>

    <HelpTip>{{ t('helpCenter.membersTags.tip') }}</HelpTip>
  </HelpArticle>
</template>
