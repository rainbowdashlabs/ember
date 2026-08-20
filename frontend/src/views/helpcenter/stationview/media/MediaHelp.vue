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

const {t} = useI18n()
</script>

<template>
    <HelpArticle :title="t('helpCenter.media.title')" :subtitle="t('helpCenter.media.subtitle')">
        <HelpSection :title="t('helpCenter.media.whatIs')">
            <p>{{ t('helpCenter.media.whatIsText') }}</p>
        </HelpSection>
        <HelpSection :title="t('helpCenter.media.uploadTitle')">
            <p>{{ t('helpCenter.media.uploadText') }}</p>
        </HelpSection>
        <HelpSection :title="t('helpCenter.media.ownTitle')">
            <p>{{ t('helpCenter.media.ownText') }}</p>
        </HelpSection>
        <HelpSection :title="t('helpCenter.media.foldersTitle')">
            <p>{{ t('helpCenter.media.foldersText') }}</p>
        </HelpSection>
        <HelpSection :title="t('helpCenter.media.tagsTitle')">
            <p>{{ t('helpCenter.media.tagsText') }}</p>
        </HelpSection>
        <HelpSection :title="t('helpCenter.media.pruneTitle')">
            <p>{{ t('helpCenter.media.pruneText') }}</p>
        </HelpSection>
        <HelpTip>{{ t('helpCenter.media.tip') }}</HelpTip>
    </HelpArticle>
</template>
