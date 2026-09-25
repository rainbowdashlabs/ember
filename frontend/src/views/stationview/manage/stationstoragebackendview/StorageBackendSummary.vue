/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'

/**
 * Where this station's files are being kept, and what it would fall back to.
 *
 * <p>Both lines, because the second is what makes the first mean anything: a station reading only
 * its own setting cannot tell an override it chose from the instance's default showing through.
 */
defineProps<{
    where: string
    instanceDefault: string
}>()

const {t} = useI18n()
</script>

<template>
    <NeutralContainer class="space-y-2">
        <SubHeader>{{ t('stationStorageBackend.summary.title') }}</SubHeader>
        <MutedText tag="p" size="sm" data-testid="station-storage-where">{{ where }}</MutedText>
        <MutedText tag="p" size="sm">
            {{ t('stationStorageBackend.summary.instanceDefault', {type: instanceDefault}) }}
        </MutedText>
    </NeutralContainer>
</template>
