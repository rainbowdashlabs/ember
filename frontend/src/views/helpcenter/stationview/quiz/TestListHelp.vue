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
import BulletList from '@/components/typography/BulletList.vue'
import {StationPermission} from '@/api/types'
import DummyTestList from '@/views/helpcenter/stationview/quiz/testlisthelp/DummyTestList.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.quiz.testListTitle')" :subtitle="t('helpCenter.quiz.testListSubtitle')">
    <HelpSection :title="t('helpCenter.quiz.whatIsTest')">
      <p>{{ t('helpCenter.quiz.whatIsTestText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.quiz.testListHow')">
      <p>{{ t('helpCenter.quiz.testListHowText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.quiz.testListExampleTitle')">
      <DummyTestList />
    </HelpSection>

    <HelpPermissionGuard :permissions="[StationPermission.TEST_CONFIGURE]" :label="t('helpCenter.permissionLabel.testConfigure')">
      <HelpSection :title="t('helpCenter.quiz.createTestTitle')">
        <p>{{ t('helpCenter.quiz.createTestText') }}</p>
        <BulletList class="mt-2">
          <li>{{ t('helpCenter.quiz.createTestName') }}</li>
          <li>{{ t('helpCenter.quiz.createTestDesc') }}</li>
          <li>{{ t('helpCenter.quiz.createTestTime') }}</li>
          <li>{{ t('helpCenter.quiz.createTestShuffle') }}</li>
        </BulletList>
      </HelpSection>
    </HelpPermissionGuard>

    <HelpTip>{{ t('helpCenter.quiz.testListTip') }}</HelpTip>
  </HelpArticle>
</template>
