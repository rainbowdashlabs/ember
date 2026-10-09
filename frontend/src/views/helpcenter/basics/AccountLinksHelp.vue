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
import BulletList from '@/components/typography/BulletList.vue'
import LinkRequestCard from '@/components/accountlink/LinkRequestCard.vue'
import MemberLinkBadge from '@/components/accountlink/MemberLinkBadge.vue'
import {ClusterUserType, LinkOrigin, LinkStatus, type LinkPrompt} from '@/api/generated/schema'

const {t} = useI18n()

const EXAMPLE: LinkPrompt = {
  uid: '00000000-0000-0000-0000-000000000001',
  stationName: t('helpCenter.basics.accountLinks.exampleStation'),
  memberName: t('helpCenter.basics.accountLinks.exampleMember'),
  origin: LinkOrigin.INVITE,
  invitedBy: t('helpCenter.basics.accountLinks.exampleInviter'),
  associationName: null,
  role: null,
  createdAt: '2026-10-08T09:00:00Z',
  expiresAt: '2026-11-07T09:00:00Z',
}

const ASSOCIATION_EXAMPLE: LinkPrompt = {
  uid: '00000000-0000-0000-0000-000000000002',
  stationName: null,
  memberName: null,
  associationName: t('helpCenter.basics.accountLinks.exampleAssociation'),
  role: ClusterUserType.CLUSTER_ADMIN,
  origin: LinkOrigin.ASSOCIATION_INVITE,
  invitedBy: null,
  createdAt: '2026-10-08T09:00:00Z',
  expiresAt: '2026-11-07T09:00:00Z',
}
</script>

<template>
  <HelpArticle :title="t('helpCenter.basics.accountLinks.title')" :subtitle="t('helpCenter.basics.accountLinks.subtitle')">
    <HelpSection :title="t('helpCenter.basics.accountLinks.whatIs')">
      <p>{{ t('helpCenter.basics.accountLinks.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.basics.accountLinks.askedTitle')">
      <p>{{ t('helpCenter.basics.accountLinks.askedText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.basics.accountLinks.howTo')">
      <BulletList>
        <li>{{ t('helpCenter.basics.accountLinks.step1') }}</li>
        <li>{{ t('helpCenter.basics.accountLinks.step2') }}</li>
        <li>{{ t('helpCenter.basics.accountLinks.step3') }}</li>
        <li>{{ t('helpCenter.basics.accountLinks.step4') }}</li>
      </BulletList>
    </HelpSection>

    <HelpSection :title="t('helpCenter.basics.accountLinks.exampleTitle')">
      <LinkRequestCard :prompt="EXAMPLE" :busy="false"/>
    </HelpSection>

    <HelpSection :title="t('helpCenter.basics.accountLinks.stationTitle')">
      <p>{{ t('helpCenter.basics.accountLinks.stationText') }}</p>
      <div class="flex flex-wrap gap-2">
        <MemberLinkBadge :status="LinkStatus.WAITING"/>
        <MemberLinkBadge :status="LinkStatus.DECLINED"/>
        <MemberLinkBadge :status="LinkStatus.EXPIRED"/>
      </div>
    </HelpSection>

    <HelpSection :title="t('helpCenter.basics.accountLinks.associationTitle')">
      <p>{{ t('helpCenter.basics.accountLinks.associationText') }}</p>
      <LinkRequestCard :prompt="ASSOCIATION_EXAMPLE" :busy="false"/>
    </HelpSection>

    <HelpSection :title="t('helpCenter.basics.accountLinks.sharedTitle')">
      <p>{{ t('helpCenter.basics.accountLinks.sharedText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.basics.accountLinks.tip') }}</HelpTip>
  </HelpArticle>
</template>
