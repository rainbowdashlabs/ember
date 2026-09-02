/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.service;

import dev.chojo.ember.feature.knowledgebase.entity.KbFile;
import dev.chojo.ember.feature.knowledgebase.entity.KbTag;
import dev.chojo.ember.feature.knowledgebase.repository.KnowledgeBaseRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * The tag vocabulary of a station's knowledge base and the tags carried by its folders and files.
 * Tags are created on demand: naming one that does not exist yet adds it to the station's
 * vocabulary.
 */
@Singleton
public class KbTagService {
    private static final Logger log = LoggerFactory.getLogger(KbTagService.class);
    private final KnowledgeBaseRepository repository;

    @Inject
    public KbTagService(KnowledgeBaseRepository repository) {
        this.repository = repository;
    }

    /**
     * Lists every tag used anywhere in a station's knowledge base.
     *
     * @param stationId the station to list for
     * @return the station's tags
     */
    public List<KbTag> findTagsByStation(int stationId) {
        return repository.findTagsByStation(stationId);
    }

    /**
     * Lists the tags carried by a file.
     *
     * @param fileId the file to list for
     * @return the file's tags
     */
    public List<KbTag> findFileTags(int fileId) {
        return repository.findFileTags(fileId);
    }

    /**
     * Replaces the tags of a file, adding any name the station does not know yet.
     *
     * @param fileId    the file to tag
     * @param tagNames  the tag names the file should carry
     * @param stationId the station the tags belong to
     * @return the file's tags after the change
     */
    public List<KbTag> setFileTags(int fileId, List<String> tagNames, int stationId) {
        repository.setFileTags(fileId, tagNames, stationId);
        log.info("Knowledge file {} now carries the tags {}", fileId, tagNames);
        return repository.findFileTags(fileId);
    }

    /**
     * Gives a file tags without touching the ones it already carries.
     *
     * <p>The counterpart of {@link #setFileTags(int, List, int)}, which replaces the whole list.
     * Setting is what a reader editing one entry means; adding is what a reader marking twenty
     * means, and using the setting form for that would strip every other tag off all twenty.
     *
     * @param fileId    the file to tag
     * @param tagNames  the tag names to add, blank ones ignored
     * @param stationId the station the tags belong to
     */
    public void addFileTags(int fileId, List<String> tagNames, int stationId) {
        for (String name : tagNames) {
            if (name == null || name.isBlank()) continue;
            repository.addFileTag(
                    fileId, repository.findOrCreateTag(stationId, name.trim()).id());
        }
    }

    /**
     * Takes tags off a file, leaving the ones not named alone.
     *
     * @param fileId    the file
     * @param tagNames  the tag names to drop, names the station does not know ignored
     * @param stationId the station the tags belong to
     */
    public void removeFileTags(int fileId, List<String> tagNames, int stationId) {
        for (String name : tagNames) {
            if (name == null || name.isBlank()) continue;
            repository
                    .findTagByName(stationId, name.trim())
                    .ifPresent(tag -> repository.removeFileTag(fileId, tag.id()));
        }
    }

    /**
     * Gives a folder tags without touching the ones it already carries.
     *
     * @param folderId  the folder to tag
     * @param tagNames  the tag names to add, blank ones ignored
     * @param stationId the station the tags belong to
     */
    public void addFolderTags(int folderId, List<String> tagNames, int stationId) {
        for (String name : tagNames) {
            if (name == null || name.isBlank()) continue;
            repository.addFolderTag(
                    folderId, repository.findOrCreateTag(stationId, name.trim()).id());
        }
    }

    /**
     * Takes tags off a folder, leaving the ones not named alone.
     *
     * @param folderId  the folder
     * @param tagNames  the tag names to drop, names the station does not know ignored
     * @param stationId the station the tags belong to
     */
    public void removeFolderTags(int folderId, List<String> tagNames, int stationId) {
        for (String name : tagNames) {
            if (name == null || name.isBlank()) continue;
            repository
                    .findTagByName(stationId, name.trim())
                    .ifPresent(tag -> repository.removeFolderTag(folderId, tag.id()));
        }
    }

    /**
     * Lists the tags carried by a folder.
     *
     * @param folderId the folder to list for
     * @return the folder's tags
     */
    public List<KbTag> findFolderTags(int folderId) {
        return repository.findFolderTags(folderId);
    }

    /**
     * Replaces the tags of a folder, adding any name the station does not know yet.
     *
     * @param folderId  the folder to tag
     * @param tagNames  the tag names the folder should carry
     * @param stationId the station the tags belong to
     * @return the folder's tags after the change
     */
    public List<KbTag> setFolderTags(int folderId, List<String> tagNames, int stationId) {
        repository.setFolderTags(folderId, tagNames, stationId);
        log.info("Knowledge folder {} now carries the tags {}", folderId, tagNames);
        return repository.findFolderTags(folderId);
    }

    /**
     * Finds every file in a station carrying a given tag.
     *
     * @param stationId the station to search in
     * @param tagName   the tag name
     * @return the tagged files
     */
    public List<KbFile> findFilesByTag(int stationId, String tagName) {
        return repository.findFilesByTag(stationId, tagName);
    }
}
