/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {onBeforeUnmount, onMounted, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute} from 'vue-router'
import DropdownMenuItem from '@/components/button/DropdownMenuItem.vue'

const props = defineProps<{
  mode: 'dropdown' | 'drawer'
  open: boolean
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'settings'): void
  (e: 'logout'): void
}>()

const {t} = useI18n()
const route = useRoute()

function onEsc(e: KeyboardEvent) {
  if (e.key === 'Escape') emit('close')
}

onMounted(() => window.addEventListener('keydown', onEsc))
onBeforeUnmount(() => window.removeEventListener('keydown', onEsc))

watch(() => route.fullPath, () => emit('close'))

function pickSettings() {
  emit('settings')
  emit('close')
}

function pickLogout() {
  emit('logout')
  emit('close')
}
</script>

<template>
  <template v-if="props.mode === 'dropdown'">
    <div v-if="props.open"
         class="absolute right-0 top-full mt-2 w-48 rounded-theme border border-(--border) bg-(--bg) shadow-lg z-50">
      <DropdownMenuItem :icon="['fas', 'gear']" @click="pickSettings">
        {{ t('header.accountSettings') }}
      </DropdownMenuItem>
      <DropdownMenuItem :icon="['fas', 'right-from-bracket']" @click="pickLogout">
        {{ t('header.logout') }}
      </DropdownMenuItem>
    </div>
  </template>

  <template v-else>
    <transition name="drawer">
      <div v-if="props.open" class="fixed inset-0 z-50 flex" @click.self="emit('close')">
        <div class="absolute inset-0 bg-black/40"/>
        <div class="relative ml-auto h-full w-72 bg-(--bg) shadow-xl flex flex-col gap-1 p-3">
          <DropdownMenuItem :icon="['fas', 'gear']" @click="pickSettings">
            {{ t('header.accountSettings') }}
          </DropdownMenuItem>
          <DropdownMenuItem :icon="['fas', 'right-from-bracket']" @click="pickLogout">
            {{ t('header.logout') }}
          </DropdownMenuItem>
        </div>
      </div>
    </transition>
  </template>
</template>

<style scoped>
.drawer-enter-active,
.drawer-leave-active {
  transition: opacity 0.15s ease;
}
.drawer-enter-from,
.drawer-leave-to {
  opacity: 0;
}
</style>
