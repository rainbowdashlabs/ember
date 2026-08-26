/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref, onMounted, onBeforeUnmount} from 'vue'
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import DropdownMenuItem from '@/components/button/DropdownMenuItem.vue'

const {t} = useI18n()

const emit = defineEmits<{
    createFolder: []
    createMarkdown: []
    upload: []
    youtube: []
    link: []
    importDocument: []
}>()

const showDropdown = ref(false)

function closeDropdown() {
    showDropdown.value = false
}

function onClickOutside(event: MouseEvent) {
    const target = event.target as HTMLElement
    if (!target.closest('.create-dropdown-container')) {
        closeDropdown()
    }
}

onMounted(() => {
    document.addEventListener('click', onClickOutside)
})

onBeforeUnmount(() => {
    document.removeEventListener('click', onClickOutside)
})

function doCreateFolder() { closeDropdown(); emit('createFolder') }
function doCreateMarkdown() { closeDropdown(); emit('createMarkdown') }
function doUpload() { closeDropdown(); emit('upload') }
function doYoutube() { closeDropdown(); emit('youtube') }
function doLink() { closeDropdown(); emit('link') }
function doImportDocument() { closeDropdown(); emit('importDocument') }
</script>

<template>
    <div class="flex flex-wrap gap-2 mb-4">
        <div class="relative create-dropdown-container">
            <PrimaryButton data-onboarding="knowledge.create" @click="showDropdown = !showDropdown">
                <font-awesome-icon :icon="['fas', 'plus']"/>
                {{ t('kb.newEntry') }}
                <font-awesome-icon :icon="['fas', 'chevron-down']" class="text-xs ml-1"/>
            </PrimaryButton>
            <div
                v-if="showDropdown"
                class="absolute left-0 top-full mt-1 z-20 min-w-48 rounded-lg border border-bg-light-accent dark:border-bg-dark-accent bg-bg-light dark:bg-bg-dark shadow-lg py-1"
            >
                <DropdownMenuItem :icon="['fas', 'folder']" icon-class="text-[var(--accent)]" @click="doCreateFolder">
                    {{ t('kb.newFolder') }}
                </DropdownMenuItem>
                <DropdownMenuItem :icon="['fas', 'file-lines']" @click="doCreateMarkdown">
                    {{ t('kb.newMarkdownFile') }}
                </DropdownMenuItem>
                <DropdownMenuItem :icon="['fas', 'upload']" @click="doUpload">
                    {{ t('kb.uploadFile') }}
                </DropdownMenuItem>
                <DropdownMenuItem :icon="['fab', 'youtube']" icon-class="text-red-600" @click="doYoutube">
                    {{ t('kb.addYoutube') }}
                </DropdownMenuItem>
                <DropdownMenuItem :icon="['fas', 'link']" icon-class="text-[var(--secondary)]" @click="doLink">
                    {{ t('kb.addLink') }}
                </DropdownMenuItem>
                <DropdownMenuItem :icon="['fas', 'file-import']" @click="doImportDocument">
                    {{ t('kb.importDocument') }}
                </DropdownMenuItem>
            </div>
        </div>
    </div>
</template>
