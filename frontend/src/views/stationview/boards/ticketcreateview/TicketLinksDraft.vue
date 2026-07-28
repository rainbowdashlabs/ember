/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { useI18n } from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import MutedIcon from '@/components/display/MutedIcon.vue'
import { LinkType } from '@/api/boards'
import type { LinkTypeName } from '@/api/boards'

export interface DraftLink { key: number; linkedTicketId: number; linkType: LinkTypeName }

export interface TicketOption {
    id: number
    ticketNumber: number
    title: string
}

defineProps<{
    links: DraftLink[]
    allTickets: TicketOption[]
    shortKey: string
}>()

const newTicketId = defineModel<string>('newTicketId', { required: true })
const newType = defineModel<LinkTypeName>('newType', { required: true })

const emit = defineEmits<{
    (e: 'add'): void
    (e: 'remove', key: number): void
}>()

const { t } = useI18n()

function linkTypeKey(linkType: string): string {
    const camel = linkType.charAt(0) + linkType.slice(1).toLowerCase().replace(/_./g, m => m.slice(1).toUpperCase())
    return 'boards.link' + camel
}

function linkedTicketLabel(linkedTicketId: number, tickets: TicketOption[], shortKey: string): string {
    const tk = tickets.find(t => t.id === linkedTicketId)
    return tk ? `${shortKey}-${tk.ticketNumber} ${tk.title}` : String(linkedTicketId)
}
</script>

<template>
    <NeutralContainer>
        <SubHeader class="mb-2">{{ t('boards.links') }}</SubHeader>
        <div v-for="link in links" :key="link.key" class="flex items-center gap-2 py-0.5 group">
            <MutedIcon :icon="['fas', 'link']" size="inline" class="shrink-0"/>
            <span class="text-xs text-(--text-muted)">{{ t(linkTypeKey(link.linkType)) }}</span>
            <span class="text-sm truncate flex-1">{{ linkedTicketLabel(link.linkedTicketId, allTickets, shortKey) }}</span>
            <IconButton :icon="['fas', 'xmark']" label="Remove" class="text-xs sm:opacity-0 sm:group-hover:opacity-100" @click="emit('remove', link.key)" />
        </div>
        <div class="flex gap-2 mt-2 items-center flex-wrap">
            <SelectInput v-model="newType" class="w-40">
                <option :value="LinkType.RELATES_TO">{{ t('boards.linkRelatesTo') }}</option>
                <option :value="LinkType.BLOCKS">{{ t('boards.linkBlocks') }}</option>
                <option :value="LinkType.BLOCKED_BY">{{ t('boards.linkBlockedBy') }}</option>
                <option :value="LinkType.CAUSES">{{ t('boards.linkCauses') }}</option>
                <option :value="LinkType.CAUSED_BY">{{ t('boards.linkCausedBy') }}</option>
            </SelectInput>
            <SelectInput v-model="newTicketId" class="flex-1 min-w-0">
                <option value="">{{ t('boards.linkedTickets') }}...</option>
                <option v-for="tk in allTickets" :key="tk.id" :value="tk.id">{{ shortKey }}-{{ tk.ticketNumber }} {{ tk.title }}</option>
            </SelectInput>
            <IconButton :icon="['fas', 'plus']" :label="t('common.add')" class="text-(--text-muted)" @click="emit('add')" />
        </div>
    </NeutralContainer>
</template>
