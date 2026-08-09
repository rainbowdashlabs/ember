/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import UserAvatar from '@/components/avatar/UserAvatar.vue'
import Td from '@/components/table/Td.vue'

const {t} = useI18n()
</script>

<template>
    <table class="w-full text-sm">
        <thead>
            <tr class="text-left text-xs text-(--text-muted) uppercase border-b border-(--border)">
                <th class="py-2 pr-3">ID</th>
                <th class="py-2 pr-3 w-full">{{ t('boards.ticketTitle') }}</th>
                <th class="py-2 pr-3">{{ t('boards.priority') }}</th>
                <th class="py-2 pr-3">{{ t('boards.assignee') }}</th>
                <th class="py-2">{{ t('boards.dueDate') }}</th>
            </tr>
        </thead>
        <tbody>
            <tr class="border-b border-(--border) cursor-pointer hover:bg-primary/5">
                <Td dense muted class="font-mono whitespace-nowrap">PLAN-7</Td>
                <td class="py-2 pr-3">{{ t('helpCenter.backlog.dummyTitle1') }}</td>
                <td class="py-2 pr-3"><font-awesome-icon :icon="['fas', 'angles-up']" class="text-xs text-red-500" /></td>
                <td class="py-2 pr-3">
                    <div class="flex items-center gap-1">
                        <UserAvatar size="sm" :name="t('helpCenter.backlog.dummyName1')" />
                        <span class="text-xs whitespace-nowrap">{{ t('helpCenter.backlog.dummyName1') }}</span>
                    </div>
                </td>
                <td class="py-2 text-xs whitespace-nowrap">15.07.2026</td>
            </tr>
            <tr class="border-b border-(--border) cursor-pointer hover:bg-primary/5">
                <Td dense muted class="font-mono whitespace-nowrap">PLAN-12</Td>
                <td class="py-2 pr-3">{{ t('helpCenter.backlog.dummyTitle2') }}</td>
                <td class="py-2 pr-3"><font-awesome-icon :icon="['fas', 'equals']" class="text-xs text-yellow-500" /></td>
                <td class="py-2 pr-3">
                    <div class="flex items-center gap-1">
                        <UserAvatar size="sm" :name="t('helpCenter.backlog.dummyName2')" />
                        <span class="text-xs whitespace-nowrap">{{ t('helpCenter.backlog.dummyName2') }}</span>
                    </div>
                </td>
                <td class="py-2 text-xs whitespace-nowrap">22.07.2026</td>
            </tr>
            <tr class="border-b border-(--border) last:border-0 cursor-pointer hover:bg-primary/5">
                <Td dense muted class="font-mono whitespace-nowrap">PLAN-15</Td>
                <td class="py-2 pr-3">{{ t('helpCenter.backlog.dummyTitle3') }}</td>
                <td class="py-2 pr-3"><font-awesome-icon :icon="['fas', 'angle-down']" class="text-xs text-blue-400" /></td>
                <td class="py-2 pr-3"></td>
                <td class="py-2 text-xs whitespace-nowrap"></td>
            </tr>
        </tbody>
    </table>
</template>
