/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'

const {t} = useI18n()
</script>

<template>
    <HelpArticle
        :title="t('helpCenter.adminStorageAudit.title')"
        :subtitle="t('helpCenter.adminStorageAudit.subtitle')"
    >
        <HelpSection :title="t('helpCenter.adminStorageAudit.what.title')">
            <p>{{ t('helpCenter.adminStorageAudit.what.text') }}</p>
        </HelpSection>
    </HelpArticle>
</template>
