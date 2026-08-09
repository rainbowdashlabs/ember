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
import HelpPermissionGuard from '@/components/helpcenter/HelpPermissionGuard.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import UserAvatar from '@/components/avatar/UserAvatar.vue'
import ProseContent from '@/components/display/ProseContent.vue'
import {StationPermission} from '@/api/types'

const {t} = useI18n()

const AUTHOR = 'Sabine Krüger'
const PUBLISHED = '02.05.2026 18:30'
const BODY = ref('<p>Am Samstag treffen wir uns um 9 Uhr an der Wache. Bringt bitte eure Einsatzkleidung mit.</p>')
</script>

<template>
  <HelpArticle :title="t('helpCenter.newsDetail.title')" :subtitle="t('helpCenter.newsDetail.subtitle')">
    <HelpSection :title="t('helpCenter.newsDetail.whatIs')">
      <p>{{ t('helpCenter.newsDetail.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.newsDetail.howTo')">
      <p>{{ t('helpCenter.newsDetail.howToStep1') }}</p>
      <p>{{ t('helpCenter.newsDetail.howToStep2') }}</p>
      <p>{{ t('helpCenter.newsDetail.howToStep3') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.newsDetail.exampleTitle')">
      <p>{{ t('helpCenter.newsDetail.exampleText') }}</p>
      <div class="space-y-4">
        <SecondaryButton :icon="['fas', 'arrow-left']" compact>{{ t('common.back') }}</SecondaryButton>
        <NeutralContainer class="space-y-3">
          <div class="flex items-start justify-between gap-3">
            <div class="flex items-center gap-2">
              <UserAvatar :name="AUTHOR" size="md"/>
              <div>
                <SubHeader class="flex items-center gap-1">
                  Gemeinsame Übung am Samstag
                  <font-awesome-icon :icon="['fas', 'lock']" class="ml-1 h-3 w-3 text-(--text-muted)"/>
                </SubHeader>
                <p class="text-xs text-(--text-muted)">{{ AUTHOR }} &middot; {{ PUBLISHED }}</p>
              </div>
            </div>
            <div class="flex items-center gap-1 shrink-0">
              <EditButton/>
              <DeleteButton/>
            </div>
          </div>
          <ProseContent v-html="BODY"/>
        </NeutralContainer>
      </div>
    </HelpSection>

    <HelpSection :title="t('helpCenter.newsDetail.lockTitle')">
      <p>{{ t('helpCenter.newsDetail.lockText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.newsDetail.commentsTitle')">
      <p>{{ t('helpCenter.newsDetail.commentsText') }}</p>
    </HelpSection>

    <HelpPermissionGuard :permissions="[StationPermission.NEWS_MANAGER]"
                         :label="t('helpCenter.permissionLabel.newsManager')">
      <HelpSection :title="t('helpCenter.newsDetail.managerTitle')">
        <p>{{ t('helpCenter.newsDetail.managerText') }}</p>
        <NeutralContainer class="space-y-3">
          <p class="text-sm">{{ t('news.deleteConfirmTitle') }}</p>
          <p class="text-sm">{{ t('news.deleteConfirmMessage') }}</p>
          <div class="flex gap-2 justify-end">
            <SecondaryButton>{{ t('common.cancel') }}</SecondaryButton>
            <ErrorButton>{{ t('common.delete') }}</ErrorButton>
          </div>
        </NeutralContainer>
      </HelpSection>
    </HelpPermissionGuard>

    <HelpTip>{{ t('helpCenter.newsDetail.tip') }}</HelpTip>
  </HelpArticle>
</template>
