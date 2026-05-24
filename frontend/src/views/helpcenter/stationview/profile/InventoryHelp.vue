/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import HelpTip from '@/components/helpcenter/HelpTip.vue'
import HelpRoleToggle from '@/components/helpcenter/HelpRoleToggle.vue'
import type {HelpRole} from '@/components/helpcenter/HelpRoleToggle.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'

const {t} = useI18n()

const roles: HelpRole[] = [
  {key: 'member', label: t('helpCenter.roles.member')},
  {key: 'memberManager', label: t('helpCenter.roles.memberManager')},
]
const activeRole = ref('')
</script>

<template>
  <HelpArticle :title="t('helpCenter.inventoryMy.title')" :subtitle="t('helpCenter.inventoryMy.subtitle')">
    <HelpSection :title="t('helpCenter.inventoryMy.whatIs')">
      <p>{{ t('helpCenter.inventoryMy.whatIsText') }}</p>
    </HelpSection>

    <HelpRoleToggle v-model="activeRole" :roles="roles"/>

    <!-- Dummy: Member selector for managers -->
    <template v-if="activeRole === 'memberManager'">
      <div class="flex items-center justify-between flex-wrap gap-3">
        <SectionHeader>{{ t('profile.inventory') }}</SectionHeader>
        <SelectInput model-value="self" class="w-48 text-sm">
          <option value="self">{{ t('profile.myInventorySelf') }}</option>
          <option value="1">Lena Mustermann</option>
          <option value="2">Tim Mustermann</option>
        </SelectInput>
      </div>
    </template>

    <!-- Dummy: Inventory groups -->
    <div class="space-y-6">
      <!-- Group: Helme -->
      <div>
        <div class="flex items-center justify-between mb-2">
          <SubHeader>Helme</SubHeader>
          <span class="text-sm text-(--text-muted)">1 / 1</span>
        </div>
        <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2">
          <NeutralContainer>
            <div class="flex items-start justify-between gap-2">
              <div>
                <div class="font-medium text-sm">
                  Helm #12 <span class="font-normal text-(--text-muted)">[M]</span>
                </div>
                <div class="text-xs text-(--text-muted)">HLM-2024-012</div>
              </div>
              <SecondaryButton class="shrink-0">
                <font-awesome-icon :icon="['fas', 'rotate']" class="mr-0.5"/>
                {{ t('profile.requestExchange') }}
              </SecondaryButton>
            </div>
          </NeutralContainer>
        </div>
      </div>

      <!-- Group: Jacken -->
      <div>
        <div class="flex items-center justify-between mb-2">
          <SubHeader>Jacken</SubHeader>
          <span class="text-sm text-(--text-muted)">
            1 / 1
          </span>
        </div>
        <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2">
          <NeutralContainer>
            <div class="flex items-start justify-between gap-2">
              <div>
                <div class="font-medium text-sm">
                  Einsatzjacke #7 <span class="font-normal text-(--text-muted)">[L]</span>
                </div>
                <div class="text-xs text-(--text-muted)">JCK-2023-007</div>
                <InfoBadge class="mt-1">Angekündigt</InfoBadge>
              </div>
            </div>
          </NeutralContainer>
        </div>
      </div>

      <!-- Group: Stiefel (with lost item) -->
      <div>
        <div class="flex items-center justify-between mb-2">
          <SubHeader>Stiefel</SubHeader>
          <span class="text-sm text-(--text-muted)">
            1 / 1
            <span class="text-error">(1 fehlt)</span>
          </span>
        </div>
        <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2">
          <NeutralContainer class="opacity-60 border-error">
            <div class="flex items-start justify-between gap-2">
              <div>
                <div class="font-medium text-sm">
                  Stiefel #3 <span class="font-normal text-(--text-muted)">[42]</span>
                </div>
                <ErrorBadge class="mt-1">{{ t('profile.lostSince') }} 01.03.2026</ErrorBadge>
              </div>
            </div>
          </NeutralContainer>
        </div>
      </div>
    </div>

    <HelpSection :title="t('helpCenter.inventoryMy.exchangeTitle')">
      <p>{{ t('helpCenter.inventoryMy.exchangeText') }}</p>
    </HelpSection>

    <!-- Dummy: Exchange request modal preview -->
    <NeutralContainer class="space-y-3">
      <SectionHeader>{{ t('profile.requestExchange') }}</SectionHeader>
      <p class="text-sm">
        Helme — Helm #12 <span class="text-(--text-muted)">[M]</span>
      </p>
      <div class="space-y-1">
        <FieldLabel>Neue Größe</FieldLabel>
        <SelectInput model-value="">
          <option value="" disabled>Größe wählen...</option>
          <option value="1">S</option>
          <option value="2">M</option>
          <option value="3">L</option>
          <option value="4">XL</option>
        </SelectInput>
      </div>
      <TextAreaInput model-value="" :placeholder="t('profile.exchangeReasonPlaceholder')" :rows="3"/>
      <div class="flex justify-end gap-2">
        <SecondaryButton>{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton disabled>{{ t('profile.submitExchange') }}</PrimaryButton>
      </div>
    </NeutralContainer>

    <HelpTip>{{ t('helpCenter.inventoryMy.tip') }}</HelpTip>
  </HelpArticle>
</template>
