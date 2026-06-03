/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import HelpTip from '@/components/helpcenter/HelpTip.vue'
</script>

<template>
    <HelpArticle title="Backlog" subtitle="Ungeplante Tickets sammeln und priorisieren">
        <HelpSection title="Was ist der Backlog?">
            <p>Der Backlog ist eine Sammlung von Tickets, die noch keiner Spalte auf dem Board zugeordnet sind. Hier kannst du Aufgaben sammeln, bevor sie eingeplant werden.</p>
        </HelpSection>

        <HelpSection title="Tickets einplanen">
            <p>Verschiebe Tickets aus dem Backlog in die erste Spalte des Boards, um sie in den Arbeitsfluss aufzunehmen. Du kannst Tickets auch direkt im Backlog erstellen.</p>
        </HelpSection>

        <HelpTip>
            Der Backlog muss in den Board-Einstellungen aktiviert werden. Wenn er deaktiviert ist, können Tickets nur direkt in einer Spalte erstellt werden.
        </HelpTip>
    </HelpArticle>
</template>
