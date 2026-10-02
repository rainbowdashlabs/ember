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
import TagListPanel from '@/views/stationview/members/tagsview/TagListPanel.vue'
import TagMembersPanel from '@/views/stationview/members/tagsview/TagMembersPanel.vue'
import {StationUserType} from '@/api/types'
import {demoCandidates, demoTagMembers, demoTags} from './fixtures'

const {t} = useI18n()
const tags = demoTags(t)
const tagMembers = demoTagMembers(t)
const candidates = demoCandidates(t)
const offeredUserTypes = [StationUserType.MEMBER]
</script>

<template>
  <HelpArticle :title="t('helpCenter.membersTags.title')" :subtitle="t('helpCenter.membersTags.subtitle')">
    <HelpSection :title="t('helpCenter.membersTags.whatIs')">
      <p>{{ t('helpCenter.membersTags.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.membersTags.differenceTitle')">
      <p>{{ t('helpCenter.membersTags.differenceText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.membersTags.exampleTitle')">
      <div class="grid gap-6 lg:grid-cols-2">
        <TagListPanel :tags="tags" :selected-tag="tags[0] ?? null" can-convert-to-group/>
        <TagMembersPanel v-if="tags[0]" :selected-tag="tags[0]" :tag-loading="false" :tag-members="tagMembers"
                         :available-members="candidates" :offered-user-types="offeredUserTypes"/>
      </div>
    </HelpSection>

    <HelpSection :title="t('helpCenter.membersTags.colorTitle')">
      <p>{{ t('helpCenter.membersTags.colorText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.membersTags.visibilityTitle')">
      <p>{{ t('helpCenter.membersTags.visibilityText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.membersTags.convertTitle')">
      <p>{{ t('helpCenter.membersTags.convertText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.membersTags.tip') }}</HelpTip>
  </HelpArticle>
</template>
