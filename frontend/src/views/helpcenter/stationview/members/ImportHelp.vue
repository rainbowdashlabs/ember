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
import SubHeader from '@/components/typography/SubHeader.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import {ref} from 'vue'

const {t} = useI18n()

const dummySeparator = ref(';')
</script>

<template>
  <HelpArticle :title="t('helpCenter.membersImport.title')" :subtitle="t('helpCenter.membersImport.subtitle')">
    <HelpSection :title="t('helpCenter.membersImport.whatIs')">
      <p>{{ t('helpCenter.membersImport.whatIsText') }}</p>
    </HelpSection>

    <!-- Dummy: Import type selector -->
    <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
      <NeutralContainer class="space-y-2 border-primary ring-2 ring-primary/30">
        <div class="flex items-center gap-2">
          <font-awesome-icon :icon="['fas', 'users']" class="text-primary"/>
          <span class="font-semibold">{{ t('memberImport.modeMembers') }}</span>
        </div>
        <p class="text-sm text-(--text-muted)">{{ t('memberImport.modeMembersHint') }}</p>
      </NeutralContainer>
      <NeutralContainer class="space-y-2 cursor-pointer hover:border-primary transition-colors">
        <div class="flex items-center gap-2">
          <font-awesome-icon :icon="['fas', 'user-shield']" class="text-primary"/>
          <span class="font-semibold">{{ t('teamImport.modeTeam') }}</span>
        </div>
        <p class="text-sm text-(--text-muted)">{{ t('teamImport.modeTeamHint') }}</p>
      </NeutralContainer>
    </div>

    <HelpSection :title="t('helpCenter.membersImport.step1')">
      <p>{{ t('helpCenter.membersImport.step1Text') }}</p>
    </HelpSection>

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
          mitglieder.csv (25 {{ t('memberImport.rows') }})
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

    <HelpSection :title="t('helpCenter.membersImport.step2')">
      <p>{{ t('helpCenter.membersImport.step2Text') }}</p>
    </HelpSection>

    <!-- Dummy: Column mapping -->
    <NeutralContainer class="space-y-4">
      <SubHeader>{{ t('memberImport.mappingTitle') }}</SubHeader>
      <p class="text-sm text-(--text-muted)">{{ t('memberImport.mappingHint') }}</p>
      <div class="space-y-2">
        <div class="rounded-lg px-3 py-2 bg-bg-light-accent/20 dark:bg-bg-dark-accent/20">
          <div class="grid grid-cols-1 sm:grid-cols-3 gap-3 items-center">
            <div>
              <span class="font-medium text-sm">Vorname</span>
              <div class="text-xs text-(--text-muted)">Max, Anna, Lisa</div>
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
              <div class="text-xs text-(--text-muted)">Mustermann, Schmidt, Weber</div>
            </div>
            <div class="sm:col-span-2 text-sm text-primary font-medium">
              → {{ t('memberImport.targetLastName') }}
            </div>
          </div>
        </div>
        <div class="rounded-lg px-3 py-2 opacity-50">
          <div class="grid grid-cols-1 sm:grid-cols-3 gap-3 items-center">
            <div>
              <span class="font-medium text-sm">Notizen</span>
              <div class="text-xs text-(--text-muted)">Anfänger, keine, —</div>
            </div>
            <div class="sm:col-span-2 text-sm text-(--text-muted)">
              → {{ t('memberImport.targetSkip') }}
            </div>
          </div>
        </div>
      </div>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.membersImport.mergeTitle')">
      <p>{{ t('helpCenter.membersImport.mergeText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.membersImport.valueMapTitle')">
      <p>{{ t('helpCenter.membersImport.valueMapText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.membersImport.step3')">
      <p>{{ t('helpCenter.membersImport.step3Text') }}</p>
    </HelpSection>

    <!-- Dummy: Result -->
    <SuccessContainer class="space-y-3">
      <SubHeader>{{ t('memberImport.done') }}</SubHeader>
      <div class="grid grid-cols-2 sm:grid-cols-5 gap-3 text-center">
        <div>
          <p class="text-xl font-bold">12</p>
          <p class="text-xs text-(--text-muted)">{{ t('memberImport.membersCreated') }}</p>
        </div>
        <div>
          <p class="text-xl font-bold">8</p>
          <p class="text-xs text-(--text-muted)">{{ t('memberImport.managersCreated') }}</p>
        </div>
        <div>
          <p class="text-xl font-bold">12</p>
          <p class="text-xs text-(--text-muted)">{{ t('memberImport.managersLinked') }}</p>
        </div>
        <div>
          <p class="text-xl font-bold">12</p>
          <p class="text-xs text-(--text-muted)">{{ t('memberImport.groupsAssigned') }}</p>
        </div>
        <div>
          <p class="text-xl font-bold">36</p>
          <p class="text-xs text-(--text-muted)">{{ t('memberImport.fieldsSet') }}</p>
        </div>
      </div>
    </SuccessContainer>

    <HelpTip>{{ t('helpCenter.membersImport.tip') }}</HelpTip>
  </HelpArticle>
</template>
