/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import Alert from '@/components/feedback/Alert.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'

/**
 * What a reader sees when a partner's file they keep as a favourite does not open.
 *
 * <p>The partner decides what it shares, and nothing here learns that it stopped until the file is
 * asked for. A bare error at that moment leaves the reader with a star pointing nowhere and no idea
 * why, so the likely reason is said and the way to take the favourite off is right beside it.
 */
defineEmits<{remove: []}>()

const {t} = useI18n()
</script>

<template>
    <Alert variant="info" class="mb-4" data-testid="kb-partner-gone">
        <p>{{ t('kb.partnerNoLongerShares') }}</p>
        <SecondaryButton class="mt-2" :icon="['fas', 'star']" @click="$emit('remove')">
            {{ t('kb.removeFavourite') }}
        </SecondaryButton>
    </Alert>
</template>
