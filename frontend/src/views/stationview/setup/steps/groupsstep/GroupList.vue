/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {MemberGroup} from '@/api/types'

defineProps<{
  groups: MemberGroup[]
  selectedId: number | null
}>()

const draft = defineModel<string>('draft', {required: true})

const emit = defineEmits<{
  (e: 'select', id: number): void
  (e: 'move', id: number, delta: -1 | 1): void
  (e: 'remove', id: number): void
  (e: 'add'): void
}>()

const {t} = useI18n()
</script>

<template>
  <div class="space-y-3">
    <SectionHeader>{{ t('setup.steps.groups.listTitle') }}</SectionHeader>
    <ul v-if="groups.length > 0" class="space-y-2">
      <li v-for="(g, idx) in groups" :key="g.id">
        <NeutralContainer
            :class="selectedId === g.id ? 'border-primary' : 'hover:border-primary'"
            class="cursor-pointer transition-colors"
            @click="emit('select', g.id)"
        >
          <div class="flex items-center gap-2">
            <span
                v-if="g.color"
                class="inline-block h-3 w-3 rounded-full shrink-0"
                :style="{backgroundColor: g.color}"
            />
            <span v-else class="inline-block h-3 w-3 rounded-full border border-(--border) shrink-0"/>
            <span class="flex-1 font-medium truncate">{{ g.name }}</span>
            <IconButton
                :icon="['fas', 'chevron-up']"
                :label="t('setup.steps.groups.moveUp')"
                :disabled="idx === 0"
                @click.stop="emit('move', g.id, -1)"
            />
            <IconButton
                :icon="['fas', 'chevron-down']"
                :label="t('setup.steps.groups.moveDown')"
                :disabled="idx === groups.length - 1"
                @click.stop="emit('move', g.id, 1)"
            />
            <DeleteButton :title="t('common.delete')" @click.stop="emit('remove', g.id)"/>
          </div>
        </NeutralContainer>
      </li>
    </ul>
    <MutedText v-else size="sm">{{ t('setup.steps.groups.emptyHint') }}</MutedText>

    <form class="flex items-center gap-2" @submit.prevent="emit('add')">
      <TextInput v-model="draft" :placeholder="t('setup.steps.groups.placeholder')" class="flex-1"/>
      <SecondaryButton type="submit">{{ t('setup.actions.addRow') }}</SecondaryButton>
    </form>
  </div>
</template>
