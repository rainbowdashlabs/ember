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
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import BulletList from '@/components/typography/BulletList.vue'
import {StationPermission} from '@/api/types'
import PagesListDummy from './pageshelp/PagesListDummy.vue'
import PageEditorSections from './pageshelp/PageEditorSections.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.pages.title')" :subtitle="t('helpCenter.pages.subtitle')">
    <HelpSection :title="t('helpCenter.pages.whatIs')">
      <p>{{ t('helpCenter.pages.whatIsText') }}</p>
      <p>{{ t('helpCenter.pages.whatIsText2') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.pages.listTitle')">
      <p>{{ t('helpCenter.pages.listText') }}</p>
      <p>{{ t('helpCenter.pages.listText2') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.pages.exampleTitle')">
      <PagesListDummy/>
    </HelpSection>

    <HelpSection :title="t('helpCenter.pages.statusTitle')">
      <p>{{ t('helpCenter.pages.statusText') }}</p>
      <BulletList>
        <li><SuccessBadge>{{ t('stationPages.published') }}</SuccessBadge> {{ t('helpCenter.pages.statusPublished') }}</li>
        <li><SecondaryBadge>{{ t('stationPages.draft') }}</SecondaryBadge> {{ t('helpCenter.pages.statusDraft') }}</li>
      </BulletList>
    </HelpSection>

    <HelpSection :title="t('helpCenter.pages.landingTitle')">
      <p>{{ t('helpCenter.pages.landingText') }}</p>
      <p>{{ t('helpCenter.pages.landingText2') }}</p>
    </HelpSection>

    <HelpPermissionGuard :permissions="[StationPermission.PAGE_EDIT]" :label="t('helpCenter.permissionLabel.pageEdit')">
      <PageEditorSections/>
    </HelpPermissionGuard>

    <HelpPermissionGuard :permissions="[StationPermission.PAGE_MANAGER]" :label="t('helpCenter.permissionLabel.pageManager')">
      <HelpSection :title="t('helpCenter.pages.managerTitle')">
        <p>{{ t('helpCenter.pages.managerText') }}</p>
        <BulletList>
          <li>{{ t('helpCenter.pages.managerPublish') }}</li>
          <li>{{ t('helpCenter.pages.managerLanding') }}</li>
          <li>{{ t('helpCenter.pages.managerDelete') }}</li>
        </BulletList>
      </HelpSection>
    </HelpPermissionGuard>

    <HelpTip>{{ t('helpCenter.pages.tip') }}</HelpTip>
  </HelpArticle>
</template>
