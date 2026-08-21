/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'

const {t} = useI18n()

const model = defineModel<boolean>({default: false})

const props = withDefaults(defineProps<{
  size?: 'sm' | 'md' | 'lg' | 'xl' | '2xl' | 'full'
  mobileFull?: boolean
}>(), {
  size: 'md',
  mobileFull: false,
})

const sizeClass = computed(() => {
  switch (props.size) {
    case 'sm': return 'max-w-md'
    case 'lg': return 'max-w-2xl'
    case 'xl': return 'max-w-5xl'
    case '2xl': return 'max-w-7xl'
    case 'full': return 'max-w-[95vw]'
    case 'md':
    default: return 'max-w-lg'
  }
})
</script>

<template>
  <Teleport to="body">
    <Transition name="modal">
      <div
          v-if="model"
          class="fixed inset-0 z-50 flex items-center justify-center"
      >
        <!-- Backdrop -->
        <div
            class="absolute inset-0 bg-black/50"
            @click="model = false"
        />
        <!-- Content -->
        <div
            data-testid="modal"
            role="dialog"
            aria-modal="true"
            :class="[
              'relative z-10 w-full mx-4 rounded-theme border border-bg-light-accent bg-bg-light p-6 shadow-xl dark:border-bg-dark-accent dark:bg-bg-dark',
              sizeClass,
              props.mobileFull ? 'max-sm:h-full max-sm:mx-0 max-sm:rounded-none max-sm:border-0 max-sm:overflow-y-auto max-sm:flex max-sm:flex-col' : '',
            ]">
          <IconButton
              :icon="['fas', 'xmark']"
              :label="t('common.close')"
              class="absolute top-3 right-3 text-[var(--text-muted)] hover:text-[var(--text)]"
              @click="model = false"
          >
            <font-awesome-icon :icon="['fas', 'xmark']" class="h-5 w-5"/>
          </IconButton>
          <slot/>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.modal-enter-active,
.modal-leave-active {
  transition: opacity 0.2s ease;
}

.modal-enter-from,
.modal-leave-to {
  opacity: 0;
}
</style>
