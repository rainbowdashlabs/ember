/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import PublicKbPanel from '@/components/knowledge/PublicKbPanel.vue'
import Alert from '@/components/feedback/Alert.vue'
import {clusterGovernance} from '@/api'
import {useSession} from '@/composables/useSession'
import {ClusterPermission} from '@/api/clusters'
import {showToast} from '@/util/toast'

/**
 * Whether the association's wiki stands on the public web.
 *
 * <p>The wiki is the one of the station the association owns, and the association cannot reach that
 * station's settings screen: running an association is not running a station. Without this panel the
 * setting had no owner at all, so the per-entry visibility field, which is drawn only where the mode is
 * on, could never appear for an association.
 *
 * <p>It sits above the wiki it governs rather than in a settings screen a click away, so the switch and
 * the thing it switches are on one page.
 */
const {t} = useI18n()
const {hasClusterPermission} = useSession()

const mode = ref('OFF')
const stationUid = ref('')
const error = ref('')
const loaded = ref(false)

const mayManage = computed(() => hasClusterPermission(ClusterPermission.CLUSTER_KNOWLEDGE_MANAGER))

const publicUrl = computed(() => stationUid.value
    ? `${window.location.origin}/public/station/${stationUid.value}/knowledge`
    : '')

/**
 * Loaded once the session says this reader may manage the association, which is not true at the moment the
 * panel mounts: the session arrives after it. Reading the permission only on mount left the panel absent
 * for good.
 */
watch(mayManage, async (may) => {
    if (!may || loaded.value) return
    try {
        const current = await clusterGovernance.getPublicKb()
        mode.value = current.mode
        stationUid.value = current.stationUid
    } catch {
        error.value = t('common.error')
    } finally {
        loaded.value = true
    }
}, {immediate: true})

watch(mode, async (next, previous) => {
    if (!loaded.value || next === previous) return
    try {
        await clusterGovernance.setPublicKb(next)
        showToast(t('stationManage.publicKb.saved'), 'success')
    } catch {
        mode.value = previous
        error.value = t('common.error')
    }
})
</script>

<template>
    <div v-if="mayManage && loaded" class="mb-4">
        <Alert v-if="error" variant="error" class="mb-2">{{ error }}</Alert>
        <PublicKbPanel v-model:mode="mode" :public-url="publicUrl"/>
    </div>
</template>
