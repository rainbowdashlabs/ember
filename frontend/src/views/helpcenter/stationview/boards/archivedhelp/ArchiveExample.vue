/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import IconButton from '@/components/button/IconButton.vue'
import BaseBadge from '@/components/badge/BaseBadge.vue'
import ArchiveTableRow from './ArchiveTableRow.vue'
import {contrastTextColor} from '@/theme/contrast'

const {t} = useI18n()

const dummyLabels = [
    {name: t('helpCenter.archived.dummyLabel1'), color: '#ec2929'},
    {name: t('helpCenter.archived.dummyLabel2'), color: '#3694FF'},
    {name: t('helpCenter.archived.dummyLabel3'), color: '#00C507'},
] as const
</script>

<template>
    <NeutralContainer class="space-y-4">
        <div class="flex items-center gap-3">
            <IconButton :icon="['fas', 'chevron-left']" label="Back" />
            <SectionHeader>PLAN — {{ t('boards.archived') }}</SectionHeader>
        </div>

        <div class="flex flex-wrap gap-1 items-center">
            <BaseBadge
                v-for="label in dummyLabels"
                :key="label.name"
                bg-class=""
                class="cursor-pointer transition-all opacity-70 hover:opacity-100"
                :style="{ backgroundColor: label.color, color: contrastTextColor(label.color) }"
            >{{ label.name }}</BaseBadge>
        </div>

        <table class="w-full text-sm">
            <thead>
                <tr class="text-left text-xs text-(--text-muted) uppercase border-b border-(--border)">
                    <th class="py-2 pr-3">ID</th>
                    <th class="py-2 pr-3 w-full">{{ t('boards.ticketTitle') }}</th>
                    <th class="py-2 pr-3">Labels</th>
                    <th class="py-2 pr-3">{{ t('boards.priority') }}</th>
                    <th class="py-2 pr-3">{{ t('boards.assignee') }}</th>
                    <th class="py-2">{{ t('boards.dueDate') }}</th>
                </tr>
            </thead>
            <tbody>
                <ArchiveTableRow
                    ticket-id="PLAN-3"
                    :title="t('helpCenter.archived.dummyTitle1')"
                    :label-name="dummyLabels[0].name"
                    :label-color="dummyLabels[0].color"
                    priority-icon="angles-up"
                    priority-class="text-red-500"
                    :assignee-name="t('helpCenter.archived.dummyName1')"
                    due-date="01.05.2026"
                />
                <ArchiveTableRow
                    ticket-id="PLAN-5"
                    :title="t('helpCenter.archived.dummyTitle2')"
                    :label-name="dummyLabels[1].name"
                    :label-color="dummyLabels[1].color"
                    priority-icon="equals"
                    priority-class="text-yellow-500"
                    :assignee-name="t('helpCenter.archived.dummyName2')"
                    due-date="10.04.2026"
                />
                <ArchiveTableRow
                    ticket-id="PLAN-8"
                    :title="t('helpCenter.archived.dummyTitle3')"
                    :label-name="dummyLabels[2].name"
                    :label-color="dummyLabels[2].color"
                    priority-icon="angle-down"
                    priority-class="text-blue-400"
                    :assignee-name="t('helpCenter.archived.dummyName1')"
                    due-date="20.03.2026"
                    last
                />
            </tbody>
        </table>
    </NeutralContainer>
</template>
