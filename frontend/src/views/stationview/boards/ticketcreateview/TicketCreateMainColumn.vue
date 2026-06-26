/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { useI18n } from 'vue-i18n'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import MarkdownEditor from '@/components/input/MarkdownEditor.vue'
import TicketChecklistDraft from './TicketChecklistDraft.vue'
import TicketWeblinksDraft from './TicketWeblinksDraft.vue'
import TicketLinksDraft from './TicketLinksDraft.vue'
import type { DraftChecklistItem } from './TicketChecklistDraft.vue'
import type { DraftWeblink } from './TicketWeblinksDraft.vue'
import type { DraftLink, TicketOption } from './TicketLinksDraft.vue'
import type { LinkTypeName } from '@/api/boards'

defineProps<{
    title: string
    description: string
    checklistItems: DraftChecklistItem[]
    newChecklistTitle: string
    weblinks: DraftWeblink[]
    newWeblinkUrl: string
    newWeblinkTitle: string
    ticketLinks: DraftLink[]
    newLinkTicketId: string
    newLinkType: LinkTypeName
    allTickets: TicketOption[]
    shortKey: string
}>()

const emit = defineEmits<{
    (e: 'update:title', value: string): void
    (e: 'update:description', value: string): void
    (e: 'update:newChecklistTitle', value: string): void
    (e: 'addChecklist'): void
    (e: 'removeChecklist', key: number): void
    (e: 'toggleChecklist', key: number): void
    (e: 'update:newWeblinkUrl', value: string): void
    (e: 'update:newWeblinkTitle', value: string): void
    (e: 'addWeblink'): void
    (e: 'removeWeblink', key: number): void
    (e: 'update:newLinkTicketId', value: string): void
    (e: 'update:newLinkType', value: LinkTypeName): void
    (e: 'addLink'): void
    (e: 'removeLink', key: number): void
}>()

const { t } = useI18n()
</script>

<template>
    <div class="lg:col-span-2 space-y-4">
        <div>
            <FieldLabel class="mb-1">{{ t('boards.ticketTitle') }} *</FieldLabel>
            <TextInput :model-value="title" @update:model-value="v => emit('update:title', String(v))" />
        </div>
        <div>
            <FieldLabel class="mb-1">{{ t('boards.ticketDescription') }}</FieldLabel>
            <MarkdownEditor :model-value="description" :placeholder="t('boards.ticketDescription')" @update:model-value="v => emit('update:description', String(v))" />
        </div>
        <TicketChecklistDraft
            :items="checklistItems"
            :new-title="newChecklistTitle"
            @update:new-title="v => emit('update:newChecklistTitle', v)"
            @add="emit('addChecklist')"
            @remove="k => emit('removeChecklist', k)"
            @toggle="k => emit('toggleChecklist', k)"
        />
        <TicketWeblinksDraft
            :weblinks="weblinks"
            :new-url="newWeblinkUrl"
            :new-title="newWeblinkTitle"
            @update:new-url="v => emit('update:newWeblinkUrl', v)"
            @update:new-title="v => emit('update:newWeblinkTitle', v)"
            @add="emit('addWeblink')"
            @remove="k => emit('removeWeblink', k)"
        />
        <TicketLinksDraft
            :links="ticketLinks"
            :all-tickets="allTickets"
            :short-key="shortKey"
            :new-ticket-id="newLinkTicketId"
            :new-type="newLinkType"
            @update:new-ticket-id="v => emit('update:newLinkTicketId', String(v))"
            @update:new-type="v => emit('update:newLinkType', v as LinkTypeName)"
            @add="emit('addLink')"
            @remove="k => emit('removeLink', k)"
        />
    </div>
</template>
