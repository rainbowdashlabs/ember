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
import SectionHeader from '@/components/typography/SectionHeader.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.membersCreate.title')" :subtitle="t('helpCenter.membersCreate.subtitle')">
    <HelpSection :title="t('helpCenter.membersCreate.howTo')">
      <p>{{ t('helpCenter.membersCreate.howToText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.membersCreate.step1')">
      <p>{{ t('helpCenter.membersCreate.roleMember') }}</p>
      <p>{{ t('helpCenter.membersCreate.roleMemberManager') }}</p>
      <p>{{ t('helpCenter.membersCreate.roleTeam') }}</p>
    </HelpSection>

    <!-- Dummy: Role selection cards -->
    <NeutralContainer class="space-y-2">
      <SectionHeader>{{ t('membersCreate.stepRole') }}</SectionHeader>
      <div class="grid gap-3 sm:grid-cols-3">
        <NeutralContainer class="text-center cursor-pointer border-primary ring-2 ring-primary/30">
          <font-awesome-icon :icon="['fas', 'user']" class="text-primary text-xl mb-1"/>
          <p class="text-sm font-semibold">{{ t('membersCreate.roleMember') }}</p>
        </NeutralContainer>
        <NeutralContainer class="text-center cursor-pointer hover:border-primary">
          <font-awesome-icon :icon="['fas', 'user-tie']" class="text-primary text-xl mb-1"/>
          <p class="text-sm font-semibold">{{ t('membersCreate.roleMemberManager') }}</p>
        </NeutralContainer>
        <NeutralContainer class="text-center cursor-pointer hover:border-primary">
          <font-awesome-icon :icon="['fas', 'user-shield']" class="text-primary text-xl mb-1"/>
          <p class="text-sm font-semibold">{{ t('membersCreate.roleTeam') }}</p>
        </NeutralContainer>
      </div>
      <div class="flex justify-end pt-2">
        <PrimaryButton>{{ t('membersCreate.next') }}</PrimaryButton>
      </div>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.membersCreate.step2')">
      <p>{{ t('helpCenter.membersCreate.step2Text') }}</p>
      <p>{{ t('helpCenter.membersCreate.step2NoLogin') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.membersCreate.step3')">
      <p>{{ t('helpCenter.membersCreate.step3Text') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.membersCreate.step4')">
      <p>{{ t('helpCenter.membersCreate.step4Text') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.membersCreate.step5')">
      <p>{{ t('helpCenter.membersCreate.step5Text') }}</p>
    </HelpSection>

    <!-- Dummy: Link to import -->
    <NeutralContainer class="flex items-center justify-between">
      <p class="text-sm">{{ t('memberImport.linkFromCreate') }}</p>
      <SecondaryButton>
        <font-awesome-icon :icon="['fas', 'upload']" class="mr-1"/>
        {{ t('memberImport.linkFromCreate') }}
      </SecondaryButton>
    </NeutralContainer>

    <HelpTip>{{ t('helpCenter.membersCreate.tip') }}</HelpTip>
  </HelpArticle>
</template>
