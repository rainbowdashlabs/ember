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
    <HelpArticle title="Boards verwalten" subtitle="Boards erstellen, bearbeiten und löschen">
        <HelpSection title="Board erstellen">
            <p>Klicke auf „Board erstellen" und gib einen Namen und ein Kürzel ein. Das Kürzel wird als Präfix für Ticket-Nummern verwendet (z.B. PLAN-1, PLAN-2).</p>
            <p class="mt-2">Nach dem Erstellen kannst du in den Einstellungen Spalten, Felder und Zugriffsrechte konfigurieren.</p>
        </HelpSection>

        <HelpSection title="Board bearbeiten">
            <p>Über das Zahnrad-Symbol gelangst du zu den Einstellungen eines Boards. Hier kannst du den Namen ändern, Spalten anpassen und Zugriffsrechte festlegen.</p>
        </HelpSection>

        <HelpSection title="Board löschen">
            <p>Ein Board kann nur gelöscht werden, wenn es keine Tickets mehr enthält. Archiviere oder lösche zuerst alle Tickets, bevor du das Board entfernst.</p>
        </HelpSection>

        <HelpTip>
            Das Kürzel eines Boards kann nach dem Erstellen nicht mehr geändert werden, da es in allen Ticket-Nummern verwendet wird.
        </HelpTip>
    </HelpArticle>
</template>
