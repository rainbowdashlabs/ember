/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.legal.service;

import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.documents.entity.DocumentTag;
import dev.chojo.ember.feature.documents.entity.SealedVersion;
import dev.chojo.ember.feature.documents.repository.DocumentRepository;
import dev.chojo.ember.feature.documents.service.DocumentService;
import dev.chojo.ember.feature.knowledgebase.service.KbFileStorageService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.service.MemberLookupService;
import dev.chojo.ember.tracking.DataTracking;
import dev.chojo.ember.tracking.DataTrackingLoader;
import dev.chojo.ember.tracking.IdentityType;
import dev.chojo.ember.tracking.engine.GenericGdprExporter;
import dev.chojo.ember.util.Json;
import dev.chojo.ember.util.TypstCompiler;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Exports all personal data associated with an account or station member in compliance with
 * GDPR/DSGVO data portability requirements (Art. 20 GDPR).
 *
 * <p>The bulk of the export is generated from {@code data_tracking.json}: every TRACKED
 * {@code gdprExport} entry whose {@code identityColumns} match the requested identity becomes a
 * query produced by {@link GenericGdprExporter}. The resulting JSON is keyed by DB table name.
 * The previous hand-coded queries with descriptive section names have been replaced - the source
 * of truth for what gets exported is now {@code data_tracking.json}.
 */
@Singleton
public class GdprExportService {

    private static final Logger log = LoggerFactory.getLogger(GdprExportService.class);

    private final AccountRepository accountRepository;
    private final StationMemberRepository stationMemberRepository;
    private final MemberLookupService memberLookupService;
    private final KbFileStorageService kbFileStorageService;
    private final DocumentRepository documentRepository;
    private final DocumentService documentService;
    private final GenericGdprExporter engine;

    @Inject
    public GdprExportService(
            AccountRepository accountRepository,
            StationMemberRepository stationMemberRepository,
            MemberLookupService memberLookupService,
            KbFileStorageService kbFileStorageService,
            DocumentRepository documentRepository,
            DocumentService documentService) {
        this.accountRepository = accountRepository;
        this.stationMemberRepository = stationMemberRepository;
        this.memberLookupService = memberLookupService;
        this.kbFileStorageService = kbFileStorageService;
        this.documentRepository = documentRepository;
        this.documentService = documentService;
        DataTracking t;
        try {
            t = DataTrackingLoader.loadFromClasspath();
        } catch (IOException e) {
            log.warn("Could not load data_tracking.json - GDPR export will be empty", e);
            t = DataTrackingLoader.empty();
        }
        this.engine = new GenericGdprExporter(t);
    }

    /**
     * Exports all personal data attached to {@code accountId}.
     *
     * <p>Result shape: {@code account} (basic profile), {@code accountTables} (every TRACKED row
     * whose {@code identityColumns} match {@code ACCOUNT_ID}), and {@code stationMemberships}
     * (one nested member-export per station this account belongs to).
     */
    public Map<String, Object> exportAccountData(int accountId) {
        var data = new LinkedHashMap<String, Object>();
        data.put("exportType", "GDPR/DSGVO Data Export");
        data.put("exportedAt", Instant.now().toString());

        accountRepository.findById(accountId).ifPresent(account -> {
            var accountData = new LinkedHashMap<String, Object>();
            accountData.put("id", account.id());
            accountData.put("email", account.email());
            accountData.put("firstName", account.firstName());
            accountData.put("lastName", account.lastName());
            accountData.put("emailVerified", account.emailVerified());
            data.put("account", accountData);
        });

        data.put("accountTables", engine.exportByIdentity(IdentityType.ACCOUNT_ID, accountId));

        var memberships = stationMemberRepository.findAllByAccountId(accountId);
        var stationDataList = new ArrayList<Map<String, Object>>();
        for (var member : memberships) {
            stationDataList.add(exportMemberData(member));
        }
        data.put("stationMemberships", stationDataList);

        return data;
    }

    /**
     * ZIP archive containing {@code data.json}, an optional {@code data.pdf}, and the user's KB files.
     */
    public byte[] exportAccountDataAsZip(int accountId, @Nullable String locale) {
        var data = exportAccountData(accountId);

        try (var baos = new ByteArrayOutputStream();
                var zip = new ZipOutputStream(baos)) {

            zip.putNextEntry(new ZipEntry("data.json"));
            zip.write(Json.MAPPER.writeValueAsBytes(data));
            zip.closeEntry();

            try {
                byte[] pdf = generatePdf(data, locale);
                if (pdf != null) {
                    zip.putNextEntry(new ZipEntry("data.pdf"));
                    zip.write(pdf);
                    zip.closeEntry();
                }
            } catch (Exception e) {
                log.warn("Failed to generate GDPR export PDF, skipping", e);
            }

            var memberships = stationMemberRepository.findAllByAccountId(accountId);
            for (var member : memberships) {
                addKbFiles(zip, member.id());
                addMemberDocuments(zip, member.stationId(), member.id());
            }

            zip.finish();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to create GDPR export ZIP", e);
        }
    }

    /**
     * Looks up a member by id and returns their metadata-driven export.
     */
    public Map<String, Object> exportMemberData(int memberId) {
        var member = stationMemberRepository.findById(memberId);
        return member.map(this::exportMemberData).orElseGet(Map::of);
    }

    /**
     * Returns the member's data as {@code memberId}, {@code stationId}, {@code former}, plus a
     * {@code memberTables} map (rows where {@code MEMBER_ID} matches) and a {@code memberUidTables}
     * map (rows where {@code MEMBER_UID} matches - used by federation-aware columns like
     * {@code news.author_member_uid}). Empty maps when no TRACKED row references the member.
     * The member's documents are exported separately, since they hang off a binding table the
     * metadata-driven exporter cannot follow.
     */
    private Map<String, Object> exportMemberData(StationMember member) {
        int mid = member.id();
        var data = new LinkedHashMap<String, Object>();
        data.put("memberId", mid);
        data.put("stationId", member.stationId());
        data.put("former", member.former());
        if (member.nickname() != null) data.put("nickname", member.nickname());
        lookupStationName(member.stationId()).ifPresent(s -> data.put("stationName", s));

        data.put("memberTables", engine.exportByIdentity(IdentityType.MEMBER_ID, mid));

        UUID memberUid = memberLookupService.resolveUid(mid);
        data.put(
                "memberUidTables",
                memberUid == null ? Map.of() : engine.exportByIdentity(IdentityType.MEMBER_UID, memberUid));

        data.put("documents", exportDocuments(member.stationId(), mid));
        return data;
    }

    private Optional<String> lookupStationName(int stationId) {
        return query("SELECT name FROM station WHERE id = :id;")
                .single(call().bind("id", stationId))
                .map(row -> row.getString("name"))
                .first();
    }

    private byte @Nullable [] generatePdf(Map<String, Object> data, @Nullable String locale) {
        String lang = locale != null && locale.startsWith("en") ? "en" : "de";
        try {
            return TypstCompiler.compileTemplate(data, lang + "/gdpr-export", null);
        } catch (Exception e) {
            log.warn("Typst PDF generation failed for GDPR export", e);
            return null;
        }
    }

    /**
     * What is kept about the documents of a member: everything except the bytes, which travel in
     * the archive beside this. Hidden ones are part of it too: what is withheld in the interface
     * is still data held about them. Only the documents of the station they are a member of, so a
     * file of another station never enters somebody's record of what is held about them.
     */
    private List<Map<String, Object>> exportDocuments(int stationId, int memberId) {
        return documentRepository.findByMember(stationId, memberId, true).stream()
                .map(document -> {
                    var entry = new LinkedHashMap<String, Object>();
                    entry.put("id", document.id());
                    entry.put("title", document.title());
                    entry.put("fileName", document.fileName());
                    entry.put("mimeType", document.mimeType());
                    entry.put("sizeBytes", document.sizeBytes());
                    entry.put("hidden", document.hidden());
                    entry.put("keptOnArchive", document.keepOnArchive());
                    entry.put("createdAt", document.createdAt().toString());
                    entry.put(
                            "tags",
                            documentRepository.findTags(document.id()).stream()
                                    .map(DocumentTag::name)
                                    .toList());
                    entry.put("sealed", document.sealed());
                    entry.put(
                            "sealedVersions",
                            documentService.sealedVersions(document).stream()
                                    .map(GdprExportService::exportSealedVersion)
                                    .toList());
                    return (Map<String, Object>) entry;
                })
                .toList();
    }

    /** One sealed version of a document: which file it is and how it was sealed, without the bytes. */
    private static Map<String, Object> exportSealedVersion(SealedVersion version) {
        var entry = new LinkedHashMap<String, Object>();
        entry.put("version", version.version());
        entry.put("sha256", version.sha256());
        entry.put("sizeBytes", version.sizeBytes());
        entry.put("sealLevel", version.sealLevel().name());
        entry.put("timestampedBy", version.timestampedBy());
        entry.put("sealedAt", version.sealedAt().toString());
        var supersededAt = version.supersededAt();
        entry.put("supersededAt", supersededAt == null ? null : supersededAt.toString());
        return entry;
    }

    /**
     * The documents themselves, so the export holds the files and not only a list of them. A sealed
     * document carries the file it serves under its usual name and every version it superseded beside
     * it, since each was a state of the document that named the member.
     */
    private void addMemberDocuments(ZipOutputStream zip, int stationId, int memberId) throws IOException {
        for (var document : documentRepository.findByMember(stationId, memberId, true)) {
            String safeName = document.fileName().replaceAll("[^a-zA-Z0-9äöüÄÖÜß._\\- ]", "_");
            var data = documentService.read(document);
            if (data.isPresent()) writeEntry(zip, "files/documents/" + document.id() + "-" + safeName, data.get());
            for (var version : documentService.sealedVersions(document)) {
                if (version.current()) continue;
                var superseded = documentService.read(document, version);
                if (superseded.isEmpty()) continue;
                writeEntry(
                        zip,
                        "files/documents/" + document.id() + "-v" + version.version() + "-" + safeName,
                        superseded.get());
            }
        }
    }

    private static void writeEntry(ZipOutputStream zip, String name, byte[] data) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(data);
        zip.closeEntry();
    }

    /**
     * The wiki articles this member wrote, the ones waiting in the trash included.
     *
     * <p>An article in the trash is still there and still theirs, so leaving it out would make the
     * export incomplete. It goes into a folder of its own instead, because handing it over beside
     * the live articles would say it is one.
     */
    private void addKbFiles(ZipOutputStream zip, int memberId) throws IOException {
        var files = query("""
                SELECT kf.id, kf.name, kf.file_type, kf.station_id, kf.deleted_at IS NOT NULL AS deleted
                FROM kb_file kf
                JOIN kb_file_version kfv ON kfv.file_id = kf.id
                WHERE kfv.version = 1 AND kfv.created_by = :member_id;
                """)
                .single(call().bind("member_id", memberId))
                .map(row -> new KbFileEntry(
                        row.getInt("id"),
                        row.getString("name"),
                        row.getString("file_type"),
                        row.getInt("station_id"),
                        row.getBoolean("deleted")))
                .all();

        for (var file : files) {
            int fileId = file.id();
            String name = file.name();
            String fileType = file.fileType();
            String safeName = name.replaceAll("[^a-zA-Z0-9äöüÄÖÜß._\\- ]", "_");
            String folder = file.deleted() ? "files/kb/deleted/" : "files/kb/";

            if ("MARKDOWN".equals(fileType) || "TEXT".equals(fileType)) {
                var textOpt = query("SELECT text_content FROM kb_file_content WHERE file_id = :id;")
                        .single(call().bind("id", fileId))
                        .map(row -> row.getString("text_content"))
                        .first();
                if (textOpt.isPresent()) {
                    String ext = "MARKDOWN".equals(fileType) ? ".md" : ".txt";
                    zip.putNextEntry(new ZipEntry(folder + safeName + ext));
                    zip.write(textOpt.get().getBytes(StandardCharsets.UTF_8));
                    zip.closeEntry();
                }
            } else {
                var fileDataOpt = kbFileStorageService.read(file.stationId(), fileId);
                if (fileDataOpt.isPresent()) {
                    String ext =
                            switch (fileType) {
                                case "PDF" -> ".pdf";
                                case "IMAGE" -> ".img";
                                default -> "";
                            };
                    zip.putNextEntry(new ZipEntry(folder + safeName + ext));
                    zip.write(fileDataOpt.get().data());
                    zip.closeEntry();
                }
            }
        }
    }

    /**
     * @param deleted whether the article is waiting in the trash, which decides where in the export
     *                it lands rather than whether it lands there at all
     */
    record KbFileEntry(int id, String name, String fileType, int stationId, boolean deleted) {}
}
