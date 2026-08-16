/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import ActionsMenu from '@/components/button/ActionsMenu.vue'
import DropdownMenuItem from '@/components/button/DropdownMenuItem.vue'
import IconButton from '@/components/button/IconButton.vue'
import type {KbItemAction} from './useKbItems'

const props = defineProps<{
    actions: KbItemAction[]
    hoverGroup?: boolean
}>()

const {t} = useI18n()

/**
 * A single action stays a plain button: opening a menu to reach one entry is a click for nothing.
 * Two or more collapse into the menu so a row never carries a strip of unlabelled icons.
 */
const single = computed(() => (props.actions.length === 1 ? props.actions[0] : null))

const hoverClass = computed(() =>
    props.hoverGroup ? 'opacity-0 group-hover:opacity-100 transition-opacity' : '')
</script>

<template>
    <div v-if="actions.length > 0" class="flex gap-1 flex-shrink-0">
        <IconButton
            v-if="single"
            :icon="single.icon"
            :label="single.label"
            :class="[single.class, single.onHover ? hoverClass : '']"
            @click.stop="single.run($event)"
        />
        <ActionsMenu
            v-else
            :label="t('kb.itemActions')"
            :class="hoverClass"
            @click.stop
        >
            <DropdownMenuItem
                v-for="action in actions"
                :key="action.key"
                :icon="action.icon"
                :icon-class="action.iconClass"
                @click="action.run($event)"
            >
                {{ action.label }}
            </DropdownMenuItem>
        </ActionsMenu>
    </div>
</template>
