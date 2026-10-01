/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import {formatDateTime} from '@/util/format'
import type {KbVersionResponse} from '@/api/generated/schema'

/** The saved versions of one wiki file, each with a button to read it and, where allowed, one to restore it. */
defineProps<{
    versions: KbVersionResponse[]
    canRevert: boolean
}>()

const emit = defineEmits<{
    view: [version: KbVersionResponse]
    revert: [version: KbVersionResponse]
}>()

const {t} = useI18n()
</script>

<template>
    <div v-if="versions.length === 0" class="text-[var(--text-muted)] text-center py-8">
        {{ t('kb.noContent') }}
    </div>

    <div v-else class="flex flex-col gap-3">
        <NeutralContainer
            v-for="version in versions"
            :key="version.id"
            data-testid="kb-version"
            :data-version="version.version"
            class="flex flex-col sm:flex-row sm:items-center gap-3"
        >
            <div class="flex-1">
                <span class="font-semibold">{{ t('kb.version') }} {{ version.version }}</span>
                <span class="text-sm text-[var(--text-muted)] ml-2">
                    {{ formatDateTime(version.createdAt) }}
                </span>
                <span
                    v-if="version.createdByName"
                    class="text-sm text-[var(--text-muted)]"
                >, {{ version.createdByName }}</span>
            </div>
            <div class="flex gap-2">
                <SecondaryButton @click="emit('view', version)">
                    <font-awesome-icon :icon="['fas', 'eye']"/>
                </SecondaryButton>
                <PrimaryButton v-if="canRevert" @click="emit('revert', version)">
                    {{ t('kb.revert') }}
                </PrimaryButton>
            </div>
        </NeutralContainer>
    </div>
</template>
