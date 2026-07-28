/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import BaseBadge from '@/components/badge/BaseBadge.vue'
import UserAvatar from '@/components/avatar/UserAvatar.vue'
import Td from '@/components/table/Td.vue'
import {contrastTextColor} from '@/theme/contrast'

defineProps<{
    ticketId: string
    title: string
    labelName: string
    labelColor: string
    priorityIcon: string
    priorityClass: string
    assigneeName: string
    dueDate: string
    last?: boolean
}>()

const {t} = useI18n()
</script>

<template>
    <tr :class="['border-b border-(--border) cursor-pointer hover:bg-primary/5', last ? 'last:border-0' : '']">
        <Td dense muted class="font-mono whitespace-nowrap">{{ ticketId }}</Td>
        <td class="py-2 pr-3">{{ title }}</td>
        <td class="py-2 pr-3">
            <div class="flex gap-1">
                <BaseBadge bg-class="" :style="{ backgroundColor: labelColor, color: contrastTextColor(labelColor) }">{{ labelName }}</BaseBadge>
            </div>
        </td>
        <td class="py-2 pr-3"><font-awesome-icon :icon="['fas', priorityIcon]" :class="['text-xs', priorityClass]" /></td>
        <td class="py-2 pr-3">
            <div class="flex items-center gap-1">
                <UserAvatar size="sm" :name="assigneeName" />
                <span class="text-xs whitespace-nowrap">{{ assigneeName }}</span>
            </div>
        </td>
        <td class="py-2 text-xs whitespace-nowrap">{{ dueDate }}</td>
    </tr>
</template>
