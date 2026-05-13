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
import SubHeader from '@/components/typography/SubHeader.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SuccessButton from '@/components/button/SuccessButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.inventoryCheckMember.title')" :subtitle="t('helpCenter.inventoryCheckMember.subtitle')">
    <HelpSection :title="t('helpCenter.inventoryCheckMember.whatShown')">
      <p>{{ t('helpCenter.inventoryCheckMember.whatShownText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryCheckMember.statusTitle')">
      <p>{{ t('helpCenter.inventoryCheckMember.statusConfirmed') }}</p>
      <p>{{ t('helpCenter.inventoryCheckMember.statusNotInPossession') }}</p>
      <p>{{ t('helpCenter.inventoryCheckMember.statusLost') }}</p>
    </HelpSection>

    <!-- Dummy: Check member view -->
    <NeutralContainer class="space-y-4">
      <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2">
        <div>
          <SectionHeader>Max Mustermann</SectionHeader>
          <p class="text-sm text-(--text-muted)">{{ t('inventory.check.title') }}</p>
        </div>
        <div class="flex gap-2">
          <SecondaryButton class="text-sm">{{ t('inventory.check.markAll') }}</SecondaryButton>
          <SecondaryButton>{{ t('inventory.check.cancel') }}</SecondaryButton>
        </div>
      </div>

      <!-- Dummy: Inventory section -->
      <NeutralContainer class="space-y-3">
        <div class="flex items-center justify-between gap-2">
          <SubHeader>Helme</SubHeader>
          <span class="text-sm text-(--text-muted) shrink-0">1 / 1</span>
        </div>

        <!-- Assigned item - confirmed -->
        <div class="rounded border border-bg-light-accent/50 dark:border-bg-dark-accent/50 p-3 space-y-2 ring-2 ring-success bg-success/10">
          <div class="flex flex-col sm:flex-row sm:items-center gap-2">
            <div class="flex-1 min-w-0">
              <div class="font-medium text-sm">Helm <span class="font-normal text-(--text-muted)">[M]</span></div>
              <div class="text-xs text-(--text-muted)">INV-0001</div>
            </div>
            <div class="flex gap-1 shrink-0">
              <SuccessButton class="text-xs px-2 py-1">
                <font-awesome-icon :icon="['fas', 'check']" />
              </SuccessButton>
              <ErrorButton class="text-xs px-2 py-1 opacity-40">
                <font-awesome-icon :icon="['fas', 'xmark']" />
              </ErrorButton>
              <SecondaryButton class="text-xs px-2 py-1">
                <font-awesome-icon :icon="['fas', 'right-from-bracket']" />
              </SecondaryButton>
            </div>
          </div>
        </div>
      </NeutralContainer>

      <NeutralContainer class="space-y-3">
        <div class="flex items-center justify-between gap-2">
          <SubHeader>Jacken</SubHeader>
          <span class="text-sm text-(--text-muted) shrink-0">
            1 / 1
          </span>
        </div>

        <!-- Assigned item - lost -->
        <div class="rounded border border-bg-light-accent/50 dark:border-bg-dark-accent/50 p-3 space-y-2 ring-2 ring-error bg-error/10">
          <div class="flex flex-col sm:flex-row sm:items-center gap-2">
            <div class="flex-1 min-w-0">
              <div class="font-medium text-sm">Jacke <span class="font-normal text-(--text-muted)">[L]</span></div>
              <div class="text-xs text-(--text-muted)">INV-0015</div>
            </div>
            <div class="flex gap-1 shrink-0">
              <SuccessButton class="text-xs px-2 py-1 opacity-40">
                <font-awesome-icon :icon="['fas', 'check']" />
              </SuccessButton>
              <ErrorButton class="text-xs px-2 py-1">
                <font-awesome-icon :icon="['fas', 'xmark']" />
              </ErrorButton>
              <SecondaryButton class="text-xs px-2 py-1">
                <font-awesome-icon :icon="['fas', 'right-from-bracket']" />
              </SecondaryButton>
              <SecondaryButton class="text-xs px-2 py-1">
                <font-awesome-icon :icon="['fas', 'folder-plus']" class="mr-1" />
                {{ t('inventory.check.createProcurement') }}
              </SecondaryButton>
            </div>
          </div>
        </div>
      </NeutralContainer>

      <!-- Dummy: Submit bar -->
      <div class="flex justify-end gap-3">
        <SecondaryButton>{{ t('inventory.check.cancel') }}</SecondaryButton>
        <PrimaryButton>{{ t('inventory.check.complete') }}</PrimaryButton>
      </div>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.inventoryCheckMember.actionsTitle')">
      <p>{{ t('helpCenter.inventoryCheckMember.actionsText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.inventoryCheckMember.tip') }}</HelpTip>
  </HelpArticle>
</template>
