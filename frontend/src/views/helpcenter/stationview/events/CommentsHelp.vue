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
import MutedText from '@/components/typography/MutedText.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import IconButton from '@/components/button/IconButton.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.comments.title')" :subtitle="t('helpCenter.comments.subtitle')">
    <HelpSection :title="t('helpCenter.comments.whatIs')">
      <p>{{ t('helpCenter.comments.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.comments.threadingTitle')">
      <p>{{ t('helpCenter.comments.threadingText') }}</p>
    </HelpSection>

    <!-- Dummy: Comment thread -->
    <HelpSection :title="t('comments.title')">
      <NeutralContainer class="space-y-4">
        <SubHeader>{{ t('comments.title') }}</SubHeader>

        <!-- Comment -->
        <div class="space-y-2">
          <div class="flex items-center gap-2">
            <span class="font-medium text-sm">{{ t('helpCenter.comments.dummyAuthor') }}</span>
            <MutedText size="sm">14:32</MutedText>
          </div>
          <p class="text-sm">{{ t('helpCenter.comments.dummyText') }}</p>
          <div class="flex items-center gap-2">
            <SecondaryButton compact :icon="['fas', 'reply']">{{ t('comments.reply') }}</SecondaryButton>
            <IconButton :icon="['fas', 'pen']" :label="t('comments.edit')"/>
            <IconButton :icon="['fas', 'trash']" :label="t('comments.delete')"/>
          </div>

          <!-- Reply (indented) -->
          <div class="ml-6 border-l-2 border-(--border) pl-4 space-y-2">
            <div class="flex items-center gap-2">
              <span class="font-medium text-sm">{{ t('helpCenter.comments.dummyReplyAuthor') }}</span>
              <MutedText size="sm">14:45</MutedText>
            </div>
            <p class="text-sm">{{ t('helpCenter.comments.dummyReplyText') }}</p>
            <div class="flex items-center gap-2">
              <SecondaryButton compact :icon="['fas', 'reply']">{{ t('comments.reply') }}</SecondaryButton>
            </div>
          </div>
        </div>

        <!-- New comment input -->
        <div class="space-y-2">
          <TextAreaInput :model-value="''" :rows="2" :placeholder="t('comments.placeholder')" disabled/>
          <div class="flex justify-end">
            <PrimaryButton :icon="['fas', 'paper-plane']">{{ t('comments.post') }}</PrimaryButton>
          </div>
        </div>
      </NeutralContainer>
    </HelpSection>

    <HelpSection :title="t('helpCenter.comments.mentionTitle')">
      <p>{{ t('helpCenter.comments.mentionText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.comments.editDeleteTitle')">
      <p>{{ t('helpCenter.comments.editDeleteText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.comments.tip') }}</HelpTip>
  </HelpArticle>
</template>
