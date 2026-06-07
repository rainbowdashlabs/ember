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
import HelpPermissionGuard from '@/components/helpcenter/HelpPermissionGuard.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PrimaryContainer from '@/components/container/PrimaryContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import {StationPermission} from '@/api/types'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.eventsManage.title')" :subtitle="t('helpCenter.eventsManage.subtitle')">
    <HelpSection :title="t('helpCenter.eventsManage.whatIs')">
      <p>{{ t('helpCenter.eventsManage.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.eventsManage.typesTitle')">
      <p>{{ t('helpCenter.eventsManage.typeRecurring') }}</p>
      <p>{{ t('helpCenter.eventsManage.typeOneTime') }}</p>
    </HelpSection>

    <!-- Dummy: Today's events -->
    <HelpSection :title="t('helpCenter.eventsManage.todayTitle')">
      <p>{{ t('helpCenter.eventsManage.todayText') }}</p>
      <div class="grid gap-3 sm:grid-cols-2 mt-3">
        <PrimaryContainer class="space-y-2">
          <div class="flex items-center justify-between">
            <span class="font-semibold">Übungsabend</span>
            <span class="text-sm">18:00 – 20:00</span>
          </div>
          <p class="text-sm text-(--text-muted)">Regulärer Übungsabend</p>
        </PrimaryContainer>
      </div>
    </HelpSection>

    <HelpPermissionGuard :permissions="[StationPermission.EVENT_EDIT]" :label="t('helpCenter.permissionLabel.eventEdit')">
      <HelpSection :title="t('helpCenter.eventsManage.createTitle')">
        <p>{{ t('helpCenter.eventsManage.createName') }}</p>
        <p>{{ t('helpCenter.eventsManage.createType') }}</p>
        <p>{{ t('helpCenter.eventsManage.createTemplate') }}</p>
        <p>{{ t('helpCenter.eventsManage.createRegistration') }}</p>
        <p>{{ t('helpCenter.eventsManage.createRestrictions') }}</p>
      </HelpSection>

      <!-- Dummy: Event list by category -->
      <HelpSection :title="t('helpCenter.eventsManage.categoriesTitle')">
        <p>{{ t('helpCenter.eventsManage.categoriesText') }}</p>
        <SubHeader class="mt-3">Übung</SubHeader>
        <div class="space-y-2">
          <NeutralContainer class="flex items-center justify-between">
            <div>
              <span class="font-medium">Übungsabend</span>
              <MutedText class="ml-2">Dienstag, 18:00 – 20:00</MutedText>
              <span class="ml-2 text-xs text-primary">{{ t('events.typeRecurring') }}</span>
            </div>
            <div class="flex items-center gap-1">
              <EditButton/>
              <DeleteButton/>
            </div>
          </NeutralContainer>
          <NeutralContainer class="flex items-center justify-between">
            <div>
              <span class="font-medium">Wettkampf Vorbereitung</span>
              <MutedText class="ml-2">25.05.2026, 14:00 – 17:00</MutedText>
              <span class="ml-2 text-xs text-primary">{{ t('events.typeOneTime') }}</span>
            </div>
            <div class="flex items-center gap-1">
              <EditButton/>
              <DeleteButton/>
            </div>
          </NeutralContainer>
        </div>

        <div class="flex justify-end mt-3">
          <PrimaryButton :icon="['fas', 'plus']">
            {{ t('events.addEvent') }}
          </PrimaryButton>
        </div>
      </HelpSection>

      <!-- Batch create mention -->
      <HelpSection :title="t('helpCenter.eventsManage.batchTitle')">
        <p>{{ t('helpCenter.eventsManage.batchText') }}</p>
      </HelpSection>

      <HelpSection :title="t('helpCenter.eventsManage.breaksTitle')">
        <p>{{ t('helpCenter.eventsManage.breaksText') }}</p>
        <p>{{ t('helpCenter.eventsManage.breaksImport') }}</p>
      </HelpSection>

      <!-- Dummy: Breaks section -->
      <HelpSection :title="t('events.breaks')">
        <SectionHeader>
          <font-awesome-icon :icon="['fas', 'umbrella-beach']" class="mr-2"/>
          {{ t('events.breaks') }}
        </SectionHeader>
        <div class="space-y-2">
          <NeutralContainer class="flex items-center justify-between">
            <div>
              <span class="font-medium">Sommerferien</span>
              <MutedText class="ml-2">01.07.2026 – 15.08.2026</MutedText>
            </div>
            <div class="flex items-center gap-1">
              <EditButton/>
              <DeleteButton/>
            </div>
          </NeutralContainer>
        </div>
        <div class="flex gap-2 mt-3">
          <PrimaryButton :icon="['fas', 'plus']">
            {{ t('events.addBreak') }}
          </PrimaryButton>
          <SecondaryButton :icon="['fas', 'download']">
            {{ t('events.importHolidays') }}
          </SecondaryButton>
        </div>
      </HelpSection>

      <!-- Export section -->
      <HelpSection :title="t('helpCenter.eventsManage.exportTitle')">
        <p>{{ t('helpCenter.eventsManage.exportText') }}</p>
        <NeutralContainer class="flex items-center justify-between mt-3">
          <SectionHeader>{{ t('events.export') }}</SectionHeader>
          <PrimaryButton :icon="['fas', 'file-export']">
            {{ t('events.exportPdf') }}
          </PrimaryButton>
        </NeutralContainer>
      </HelpSection>
    </HelpPermissionGuard>

    <HelpTip>{{ t('helpCenter.eventsManage.tip') }}</HelpTip>
  </HelpArticle>
</template>
