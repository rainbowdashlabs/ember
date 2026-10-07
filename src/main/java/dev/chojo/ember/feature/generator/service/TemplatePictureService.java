/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.feature.generator.entity.DocumentTemplate;
import dev.chojo.ember.feature.generator.service.store.OwnerStores;
import dev.chojo.ember.feature.media.entity.MediaContent;
import dev.chojo.ember.feature.media.image.ImageProfile;
import dev.chojo.ember.feature.media.service.ImageVariants;
import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.owner.Owner;
import dev.chojo.ember.util.FilePicture;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The picture of a document template: its first page, drawn without a member so its placeholders show
 * by their labels and no picture ever carries anybody's data. It is what lets a template be chosen by
 * what it looks like.
 *
 * <p>Drawn the first time somebody asks for it and kept per version, under
 * {@code <id>/v<version>-<dpi>}. A template saved again is a new version, whose picture is drawn on its
 * first request; the pictures of the versions before go then, so a template never shows how it looked
 * before it was changed. The resolution in the key does the same for a page drawn more finely than
 * before: the coarser picture is replaced the next time it is asked for.
 *
 * <p>A template no picture can be drawn of, one whose PDF is gone or will not draw, is remembered as
 * such for its version until the process restarts, so a list showing it does not draw and fail on
 * every visit.
 */
@Singleton
public class TemplatePictureService {
    private static final Logger log = LoggerFactory.getLogger(TemplatePictureService.class);
    private static final ImageProfile PROFILE = ImageProfile.CONTENT;

    private final DocumentTemplateService templates;
    private final DocumentGeneratorService generator;
    private final ImageVariants images;
    private final OwnerStores stores;
    private final Set<String> undrawable = ConcurrentHashMap.newKeySet();

    @Inject
    public TemplatePictureService(
            DocumentTemplateService templates,
            DocumentGeneratorService generator,
            ImageVariants images,
            OwnerStores stores) {
        this.templates = templates;
        this.generator = generator;
        this.images = images;
        this.stores = stores;
    }

    /**
     * The picture of a template a station may use: its own, or one of its association.
     *
     * @param stationId  the station
     * @param templateId the template
     * @param size       the longest side wanted, answered by the nearest size kept at or above it
     * @return the picture, or empty where none could be drawn
     */
    public Optional<MediaContent> forStation(int stationId, int templateId, int size) {
        return pictureOf(templates.requireUsable(stationId, templateId), size);
    }

    /**
     * The picture of a template of its owner, as the association's own list shows it.
     *
     * @param owner      the station or the association keeping the template
     * @param templateId the template
     * @param size       the longest side wanted
     * @return the picture, or empty where none could be drawn
     */
    public Optional<MediaContent> forOwner(Owner owner, int templateId, int size) {
        return pictureOf(templates.requireOwned(owner, templateId), size);
    }

    private Optional<MediaContent> pictureOf(DocumentTemplate template, int size) {
        var scope = stores.scopeOf(template.owner(), DocumentRefusal.DOCUMENT_TEMPLATE_NOT_HERE);
        var category = categoryOf(template.owner());
        String key = key(template);
        var kept = images.read(PROFILE, scope, category, key, size);
        if (kept.isPresent() || undrawable.contains(key)) return kept;
        try {
            var picture = FilePicture.of("application/pdf", generator.drawWithoutMember(generator.sourceOf(template)));
            if (picture.isEmpty()) {
                undrawable.add(key);
                return Optional.empty();
            }
            images.delete(scope, category, template.id() + "/");
            images.store(PROFILE, scope, category, key, picture.get(), 0);
        } catch (Exception e) {
            undrawable.add(key);
            log.warn("No picture could be drawn of document template {}", template.id(), e);
            return Optional.empty();
        }
        return images.read(PROFILE, scope, category, key, size);
    }

    private static StorageCategory categoryOf(Owner owner) {
        return owner instanceof Owner.Association
                ? StorageCategory.IMAGE_ASSOCIATION_DOCUMENT_TEMPLATE_PICTURE
                : StorageCategory.IMAGE_DOCUMENT_TEMPLATE_PICTURE;
    }

    private static String key(DocumentTemplate template) {
        return template.id() + "/v" + template.version() + "-" + FilePicture.PAGE_DPI;
    }
}
