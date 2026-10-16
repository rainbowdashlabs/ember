/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {watchDebounced} from '@vueuse/core'
import TextInput from '@/components/input/text/TextInput.vue'
import ChipButton from '@/components/button/ChipButton.vue'
import BareButton from '@/components/button/BareButton.vue'
import UserAvatar from '@/components/avatar/UserAvatar.vue'
import MutedText from '@/components/typography/MutedText.vue'
import AppIcon from '@/components/display/AppIcon.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import type {ReachablePersonResponse} from '@/api/generated/schema'
import {findPeople} from '@/api/messages'
import {personName} from '@/util/conversationTitle'

/**
 * Finds people to write to: everybody who shares a station with the reader, searched by name,
 * each with one station they share. Picking somebody adds them to the chosen, picking them again
 * takes them out.
 */
const props = withDefaults(defineProps<{
  /** People already in the conversation, who are not offered again. */
  exclude?: readonly string[]
}>(), {
  exclude: () => [],
})

const chosen = defineModel<ReachablePersonResponse[]>({required: true})

const {t} = useI18n()
const search = ref('')
const found = ref<ReachablePersonResponse[]>([])

async function look() {
  found.value = await findPeople(search.value).catch(() => [])
}

onMounted(look)
watchDebounced(search, look, {debounce: 250})

const offered = computed(() => found.value.filter(person => !props.exclude.includes(person.uid)))

function isChosen(person: ReachablePersonResponse): boolean {
  return chosen.value.some(entry => entry.uid === person.uid)
}

function toggle(person: ReachablePersonResponse) {
  chosen.value = isChosen(person)
    ? chosen.value.filter(entry => entry.uid !== person.uid)
    : [...chosen.value, person]
}
</script>

<template>
  <div class="space-y-3">
    <TextInput v-model="search" :placeholder="t('messages.picker.search')" data-testid="messages-person-search"/>
    <div v-if="chosen.length > 0" class="flex flex-wrap gap-2">
      <ChipButton v-for="person in chosen" :key="person.uid" @click="toggle(person)">
        {{ personName(person) }}
        <AppIcon :icon="['fas', 'xmark']" class="h-3 w-3"/>
      </ChipButton>
    </div>
    <ul class="max-h-72 overflow-y-auto divide-y divide-(--border)">
      <li v-for="person in offered" :key="person.uid">
        <BareButton :aria-pressed="isChosen(person)" :data-testid="`messages-person-${person.uid}`"
                    class="flex w-full items-center gap-3 px-2 py-2 text-left hover:bg-bg-light-accent dark:hover:bg-bg-dark-accent"
                    @click="toggle(person)">
          <UserAvatar :identity="{accountUid: person.uid}" :name="personName(person)" size="sm"/>
          <span class="min-w-0 flex-1">
            <span class="block truncate text-sm">{{ personName(person) }}</span>
            <MutedText class="block truncate">{{ person.stationName }}</MutedText>
          </span>
          <AppIcon v-if="isChosen(person)" :icon="['fas', 'check']" class="h-4 w-4 text-primary"/>
        </BareButton>
      </li>
    </ul>
    <EmptyState v-if="offered.length === 0" compact :message="t('messages.picker.none')"/>
  </div>
</template>
