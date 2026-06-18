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
    <HelpArticle :title="t('helpCenter.pageFiles.title')" :subtitle="t('helpCenter.pageFiles.subtitle')">
        <HelpSection :title="t('helpCenter.pageFiles.whatIs')">
            <p>{{ t('helpCenter.pageFiles.whatIsText') }}</p>
        </HelpSection>
        <HelpSection :title="t('helpCenter.pageFiles.uploadTitle')">
            <p>{{ t('helpCenter.pageFiles.uploadText') }}</p>
        </HelpSection>
        <HelpSection :title="t('helpCenter.pageFiles.foldersTitle')">
            <p>{{ t('helpCenter.pageFiles.foldersText') }}</p>
        </HelpSection>
        <HelpSection :title="t('helpCenter.pageFiles.tagsTitle')">
            <p>{{ t('helpCenter.pageFiles.tagsText') }}</p>
        </HelpSection>
        <HelpSection :title="t('helpCenter.pageFiles.pruneTitle')">
            <p>{{ t('helpCenter.pageFiles.pruneText') }}</p>
        </HelpSection>
        <HelpTip>{{ t('helpCenter.pageFiles.tip') }}</HelpTip>
    </HelpArticle>
</template>
