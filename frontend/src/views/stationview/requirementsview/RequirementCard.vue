/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'

/** One thing still owed, offered with the single action that settles it. */
defineProps<{
    icon: string
    title: string
    text: string
    action: string
}>()

defineEmits<{open: []}>()
</script>

<template>
    <NeutralContainer>
        <div class="flex items-center gap-3">
            <font-awesome-icon :icon="['fas', icon]" class="text-primary text-xl" />
            <div class="flex-1">
                <SubHeader>{{ title }}</SubHeader>
                <p class="text-sm text-(--text-muted)">{{ text }}</p>
            </div>
            <PrimaryButton @click="$emit('open')">{{ action }}</PrimaryButton>
        </div>
    </NeutralContainer>
</template>
