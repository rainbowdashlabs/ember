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
import SuccessContainer from '@/components/container/SuccessContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import {ref} from 'vue'

const {t} = useI18n()

const dummySeparator = ref(';')
</script>

<template>
  <HelpArticle :title="t('helpCenter.membersImportTeam.title')" :subtitle="t('helpCenter.membersImportTeam.subtitle')">
    <HelpSection :title="t('helpCenter.membersImportTeam.whatIs')">
      <p>{{ t('helpCenter.membersImportTeam.whatIsText') }}</p>
    </HelpSection>

    <!-- Dummy: Header with back button -->
    <div class="flex items-center justify-between">
      <SectionHeader>{{ t('teamImport.title') }}</SectionHeader>
      <SecondaryButton>
        <font-awesome-icon :icon="['fas', 'chevron-left']" class="mr-2"/>
        {{ t('common.back') }}
      </SecondaryButton>
    </div>

    <!-- Dummy: Upload step -->
    <NeutralContainer class="space-y-4">
      <SubHeader>{{ t('memberImport.upload') }}</SubHeader>
      <div class="flex items-center gap-4 flex-wrap">
        <SecondaryButton>
          <font-awesome-icon :icon="['fas', 'upload']" class="mr-1"/>
          {{ t('memberImport.chooseFile') }}
        </SecondaryButton>
        <span class="text-sm">
          <font-awesome-icon :icon="['fas', 'check']" class="text-success mr-1"/>
          team.csv (5 {{ t('memberImport.rows') }})
        </span>
      </div>
      <div class="flex items-center gap-2">
        <label class="text-sm font-medium">{{ t('memberImport.separator') }}</label>
        <SelectInput v-model="dummySeparator" class="w-20">
          <option value=";">;</option>
          <option value=",">,</option>
          <option value="&#9;">Tab</option>
        </SelectInput>
      </div>
      <PrimaryButton>{{ t('memberImport.next') }}</PrimaryButton>
    </NeutralContainer>

    <!-- Dummy: Column mapping -->
    <NeutralContainer class="space-y-4">
      <SubHeader>{{ t('memberImport.mappingTitle') }}</SubHeader>
      <p class="text-sm text-(--text-muted)">{{ t('memberImport.mappingHint') }}</p>
      <div class="space-y-2">
        <div class="rounded-lg px-3 py-2 bg-bg-light-accent/20 dark:bg-bg-dark-accent/20">
          <div class="grid grid-cols-1 sm:grid-cols-3 gap-3 items-center">
            <div>
              <span class="font-medium text-sm">Vorname</span>
              <div class="text-xs text-(--text-muted)">Lisa, Tom, Sarah</div>
            </div>
            <div class="sm:col-span-2 text-sm text-primary font-medium">
              → {{ t('memberImport.targetFirstName') }}
            </div>
          </div>
        </div>
        <div class="rounded-lg px-3 py-2 bg-bg-light-accent/20 dark:bg-bg-dark-accent/20">
          <div class="grid grid-cols-1 sm:grid-cols-3 gap-3 items-center">
            <div>
              <span class="font-medium text-sm">Nachname</span>
              <div class="text-xs text-(--text-muted)">Weber, Fischer, Braun</div>
            </div>
            <div class="sm:col-span-2 text-sm text-primary font-medium">
              → {{ t('memberImport.targetLastName') }}
            </div>
          </div>
        </div>
        <div class="rounded-lg px-3 py-2 bg-bg-light-accent/20 dark:bg-bg-dark-accent/20">
          <div class="grid grid-cols-1 sm:grid-cols-3 gap-3 items-center">
            <div>
              <span class="font-medium text-sm">E-Mail</span>
              <div class="text-xs text-(--text-muted)">lisa@example.com, tom@example.com</div>
            </div>
            <div class="sm:col-span-2 text-sm text-primary font-medium">
              → {{ t('memberImport.targetEmail') }}
            </div>
          </div>
        </div>
      </div>
    </NeutralContainer>

    <!-- Dummy: Result -->
    <SuccessContainer class="space-y-3">
      <SubHeader>{{ t('memberImport.done') }}</SubHeader>
      <div class="grid grid-cols-3 gap-3 text-center">
        <div>
          <p class="text-xl font-bold">5</p>
          <p class="text-xs text-(--text-muted)">{{ t('teamImport.membersCreated') }}</p>
        </div>
        <div>
          <p class="text-xl font-bold">5</p>
          <p class="text-xs text-(--text-muted)">{{ t('memberImport.groupsAssigned') }}</p>
        </div>
        <div>
          <p class="text-xl font-bold">10</p>
          <p class="text-xs text-(--text-muted)">{{ t('memberImport.fieldsSet') }}</p>
        </div>
      </div>
    </SuccessContainer>

    <HelpTip>{{ t('helpCenter.membersImportTeam.tip') }}</HelpTip>
  </HelpArticle>
</template>
