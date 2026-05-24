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
import ErrorButton from '@/components/button/ErrorButton.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import IconButton from '@/components/button/IconButton.vue'
import MutedText from '@/components/typography/MutedText.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.membersDetail.title')" :subtitle="t('helpCenter.membersDetail.subtitle')">
    <HelpSection :title="t('helpCenter.membersDetail.whatShown')">
      <p>{{ t('helpCenter.membersDetail.whatShownText') }}</p>
    </HelpSection>

    <!-- Dummy: Member header -->
    <div class="flex items-center justify-between">
      <SecondaryButton :icon="['fas', 'chevron-left']">
        {{ t('memberDetail.back') }}
      </SecondaryButton>
      <div class="flex items-center gap-2">
        <ErrorButton :icon="['fas', 'user-slash']">
          {{ t('memberDetail.markFormer') }}
        </ErrorButton>
        <ErrorButton :icon="['fas', 'trash']">
          {{ t('memberDetail.deleteData') }}
        </ErrorButton>
        <PrimaryButton :icon="['fas', 'pen']">
          {{ t('memberDetail.edit') }}
        </PrimaryButton>
      </div>
    </div>

    <SectionHeader>Max Mustermann</SectionHeader>
    <p class="text-sm text-(--text-muted)">max@example.com</p>

    <HelpSection :title="t('helpCenter.membersDetail.fieldsTitle')">
      <p>{{ t('helpCenter.membersDetail.fieldsText') }}</p>
    </HelpSection>

    <!-- Dummy: Profile fields -->
    <NeutralContainer class="space-y-3">
      <SubHeader class="text-sm">{{ t('memberDetail.fields') }}</SubHeader>
      <div class="grid gap-2 sm:grid-cols-2">
        <div class="text-sm">
          <span class="text-(--text-muted)">{{ t('helpCenter.exampleFields.phone') }}:</span>
          <span class="ml-1 font-medium">0170 1234567</span>
        </div>
        <div class="text-sm">
          <span class="text-(--text-muted)">{{ t('helpCenter.exampleFields.birthday') }}:</span>
          <span class="ml-1 font-medium">15.03.2010</span>
        </div>
        <div class="text-sm">
          <span class="text-(--text-muted)">{{ t('helpCenter.exampleFields.clothingSize') }}:</span>
          <span class="ml-1 font-medium">M</span>
        </div>
        <div class="text-sm">
          <span class="text-(--text-muted)">{{ t('helpCenter.exampleFields.emergencyContact') }}:</span>
          <span class="ml-1 font-medium">Petra Mustermann</span>
        </div>
      </div>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.membersDetail.managersTitle')">
      <p>{{ t('helpCenter.membersDetail.managersText') }}</p>
    </HelpSection>

    <!-- Dummy: Managers -->
    <NeutralContainer class="space-y-4">
      <div class="flex items-center justify-between">
        <SubHeader class="text-sm">{{ t('memberDetail.managers') }}</SubHeader>
        <div class="flex items-center gap-2">
          <SecondaryButton :icon="['fas', 'link']">
            {{ t('memberDetail.linkManager') }}
          </SecondaryButton>
          <SecondaryButton :icon="['fas', 'plus']">
            {{ t('memberDetail.createManager') }}
          </SecondaryButton>
        </div>
      </div>
      <div class="rounded-lg px-4 py-3 bg-bg-light-accent/30 dark:bg-bg-dark-accent/30 space-y-2">
        <div class="flex items-center justify-between">
          <div>
            <span class="font-semibold">Petra Mustermann</span>
            <MutedText class="ml-2">petra@example.com</MutedText>
          </div>
          <div class="flex items-center gap-2">
            <EditButton/>
            <DeleteButton/>
          </div>
        </div>
        <div class="grid gap-2 sm:grid-cols-2">
          <div class="text-sm">
            <span class="text-(--text-muted)">{{ t('helpCenter.exampleFields.phone') }}:</span>
            <span class="ml-1 font-medium">0171 9876543</span>
          </div>
        </div>
      </div>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.membersDetail.inventoryTitle')">
      <p>{{ t('helpCenter.membersDetail.inventoryText') }}</p>
      <p>{{ t('helpCenter.membersDetail.inventoryAssign') }}</p>
      <p>{{ t('helpCenter.membersDetail.inventoryUnassign') }}</p>
      <p>{{ t('helpCenter.membersDetail.inventoryReassign') }}</p>
      <p>{{ t('helpCenter.membersDetail.inventoryExchange') }}</p>
    </HelpSection>

    <!-- Dummy: Inventory items with management actions -->
    <NeutralContainer class="space-y-3">
      <div class="flex items-center justify-between">
        <SubHeader>{{ t('memberDetail.inventory') }}</SubHeader>
        <PrimaryButton :icon="['fas', 'plus']" disabled>
          {{ t('memberDetail.assignItem') }}
        </PrimaryButton>
      </div>
      <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2">
        <NeutralContainer>
          <div class="flex items-start justify-between gap-2">
            <div>
              <div class="font-medium text-sm">Helm <span class="font-normal text-(--text-muted)">M</span></div>
              <div class="text-xs text-(--text-muted)">Helme</div>
              <div class="text-xs text-(--text-muted)">#H-042</div>
            </div>
            <div class="flex items-center gap-1">
              <IconButton :icon="['fas', 'rotate']" label="Tausch" disabled/>
              <IconButton :icon="['fas', 'arrow-right-arrow-left']" label="Umzuweisen" disabled/>
              <IconButton :icon="['fas', 'xmark']" label="Aufheben" disabled/>
            </div>
          </div>
        </NeutralContainer>
        <NeutralContainer>
          <div class="flex items-start justify-between gap-2">
            <div>
              <div class="font-medium text-sm">Jacke <span class="font-normal text-(--text-muted)">L</span></div>
              <div class="text-xs text-(--text-muted)">Jacken</div>
              <div class="text-xs text-(--text-muted)">#J-018</div>
            </div>
            <div class="flex items-center gap-1">
              <IconButton :icon="['fas', 'rotate']" label="Tausch" disabled/>
              <IconButton :icon="['fas', 'arrow-right-arrow-left']" label="Umzuweisen" disabled/>
              <IconButton :icon="['fas', 'xmark']" label="Aufheben" disabled/>
            </div>
          </div>
        </NeutralContainer>
      </div>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.membersDetail.changesTitle')">
      <p>{{ t('helpCenter.membersDetail.changesText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.membersDetail.formerTitle')">
      <p>{{ t('helpCenter.membersDetail.formerText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.membersDetail.tip') }}</HelpTip>
  </HelpArticle>
</template>
