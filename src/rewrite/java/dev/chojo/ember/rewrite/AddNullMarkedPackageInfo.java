/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.rewrite;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.SourceFile;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.marker.JavaSourceSet;
import org.openrewrite.java.tree.J;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * Gives every package of the main sources a {@code package-info.java} that marks it {@code @NullMarked},
 * optionally with the SpotBugs defaults that make SpotBugs hold the package to the same rule.
 *
 * <p>jspecify's {@code @NullMarked} says a reference in the package is non-null unless it is marked
 * {@code @Nullable}. SpotBugs reads {@code @NullMarked} only where it sits on a method, never as a
 * package default, so with {@code spotBugsDefaults} the file also carries
 * {@code @DefaultAnnotationForParameters(NonNull.class)} and {@code @DefaultAnnotationForMethods(NonNull.class)}.
 * Fields get no default: SpotBugs cannot read jspecify on the field behind a record component.
 *
 * <p>A package without the file gets a new one. A package whose file carries no more than these
 * annotations is brought to the wanted set. A file that says anything else, such as a package
 * comment or another annotation, is left alone and has to be changed by hand.
 */
public final class AddNullMarkedPackageInfo extends ScanningRecipe<AddNullMarkedPackageInfo.Packages> {

    private static final String PACKAGE_INFO = "package-info.java";

    private static final Set<String> KNOWN_ANNOTATIONS =
            Set.of("NullMarked", "DefaultAnnotationForParameters", "DefaultAnnotationForMethods");

    private static final String HEADER = """
            /*
             *     SPDX-License-Identifier: AGPL-3.0-only
             *
             *     Copyright (C) RainbowDashLabs and Contributor
             */
            """;

    @Option(
            displayName = "Packages",
            description =
                    "Comma separated packages to reach, each with the packages below it. Every package when left out.",
            example = "dev.chojo.ember.feature.inventory",
            required = false)
    private final @Nullable String packages;

    @Option(
            displayName = "SpotBugs defaults",
            description = "Also mark parameters and method returns non-null by default for SpotBugs.",
            required = false)
    private final @Nullable Boolean spotBugsDefaults;

    /**
     * Creates the recipe.
     *
     * @param packages         comma separated packages to reach, or {@code null} for every package
     * @param spotBugsDefaults whether the SpotBugs defaults are added as well
     */
    @JsonCreator
    public AddNullMarkedPackageInfo(
            @JsonProperty("packages") @Nullable String packages,
            @JsonProperty("spotBugsDefaults") @Nullable Boolean spotBugsDefaults) {
        this.packages = packages;
        this.spotBugsDefaults = spotBugsDefaults;
    }

    /**
     * The packages a scan found: where each one's sources live and which already have the file.
     */
    public static final class Packages {
        private final Map<String, Path> directories = new TreeMap<>();
        private final Set<String> described = new HashSet<>();
    }

    @Override
    public String getDisplayName() {
        return "Mark packages null-marked";
    }

    @Override
    public String getDescription() {
        return "Adds a `package-info.java` with jspecify `@NullMarked`, and optionally the SpotBugs non-null defaults, to every package of the main sources.";
    }

    /**
     * The packages option.
     *
     * @return the packages to reach, or {@code null} for every package
     */
    public @Nullable String getPackages() {
        return packages;
    }

    /**
     * The SpotBugs defaults option.
     *
     * @return whether the SpotBugs defaults are added, or {@code null} when not
     */
    public @Nullable Boolean getSpotBugsDefaults() {
        return spotBugsDefaults;
    }

    @Override
    public Packages getInitialValue(ExecutionContext ctx) {
        return new Packages();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Packages acc) {
        PackageFilter filter = PackageFilter.of(packages);
        return new JavaIsoVisitor<>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit unit, ExecutionContext ctx) {
                String name = packageOf(unit);
                if (name == null || !isMainSource(unit) || !filter.includesPackage(name)) {
                    return unit;
                }
                Path path = unit.getSourcePath();
                if (PACKAGE_INFO.equals(String.valueOf(path.getFileName()))) {
                    acc.described.add(name);
                } else if (path.getParent() != null) {
                    acc.directories.putIfAbsent(name, path.getParent());
                }
                return unit;
            }
        };
    }

    @Override
    public Collection<? extends SourceFile> generate(Packages acc, ExecutionContext ctx) {
        List<SourceFile> created = new ArrayList<>();
        acc.directories.forEach((name, directory) -> {
            if (!acc.described.contains(name)) {
                created.add(parse(name, ctx).withSourcePath(directory.resolve(PACKAGE_INFO)));
            }
        });
        return created;
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Packages acc) {
        PackageFilter filter = PackageFilter.of(packages);
        return new JavaIsoVisitor<>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit unit, ExecutionContext ctx) {
                String name = packageOf(unit);
                if (name == null
                        || !isMainSource(unit)
                        || !filter.includesPackage(name)
                        || !PACKAGE_INFO.equals(
                                String.valueOf(unit.getSourcePath().getFileName()))
                        || !isRewritable(unit)
                        || hasWantedAnnotations(unit)) {
                    return unit;
                }
                return parse(name, ctx)
                        .withId(unit.getId())
                        .withSourcePath(unit.getSourcePath())
                        .withMarkers(unit.getMarkers())
                        .withEof(unit.getEof());
            }
        };
    }

    private boolean withDefaults() {
        return Boolean.TRUE.equals(spotBugsDefaults);
    }

    private boolean hasWantedAnnotations(J.CompilationUnit unit) {
        Set<String> present = annotationNames(unit);
        Set<String> wanted = withDefaults() ? KNOWN_ANNOTATIONS : Set.of("NullMarked");
        return present.containsAll(wanted);
    }

    private static boolean isRewritable(J.CompilationUnit unit) {
        J.Package declaration = unit.getPackageDeclaration();
        return declaration != null
                && KNOWN_ANNOTATIONS.containsAll(annotationNames(unit))
                && unit.getClasses().isEmpty()
                && !unit.printAll().contains("/**");
    }

    private static Set<String> annotationNames(J.CompilationUnit unit) {
        J.Package declaration = unit.getPackageDeclaration();
        Set<String> names = new HashSet<>();
        if (declaration != null) {
            declaration.getAnnotations().forEach(annotation -> names.add(annotation.getSimpleName()));
        }
        return names;
    }

    private static @Nullable String packageOf(J.CompilationUnit unit) {
        J.Package declaration = unit.getPackageDeclaration();
        return declaration == null ? null : declaration.getPackageName();
    }

    private static boolean isMainSource(J.CompilationUnit unit) {
        return unit.getMarkers()
                .findFirst(JavaSourceSet.class)
                .map(set -> "main".equals(set.getName()))
                .orElse(true);
    }

    private J.CompilationUnit parse(String packageName, ExecutionContext ctx) {
        String source = text(packageName, withDefaults());
        return JavaParser.fromJavaVersion()
                .build()
                .parse(ctx, source)
                .map(J.CompilationUnit.class::cast)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Could not parse the package-info of " + packageName));
    }

    /**
     * The whole {@code package-info.java} for a package, as Spotless would leave it.
     *
     * @param packageName  the package
     * @param withDefaults whether the SpotBugs defaults are part of it
     * @return the file's text
     */
    static String text(String packageName, boolean withDefaults) {
        StringBuilder text = new StringBuilder(HEADER).append("@NullMarked\n");
        if (withDefaults) {
            text.append("@DefaultAnnotationForMethods(NonNull.class)\n")
                    .append("@DefaultAnnotationForParameters(NonNull.class)\n");
        }
        text.append("package ").append(packageName).append(";\n\n");
        if (withDefaults) {
            text.append("import edu.umd.cs.findbugs.annotations.DefaultAnnotationForMethods;\n")
                    .append("import edu.umd.cs.findbugs.annotations.DefaultAnnotationForParameters;\n")
                    .append("import edu.umd.cs.findbugs.annotations.NonNull;\n");
        }
        return text.append("import org.jspecify.annotations.NullMarked;\n").toString();
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return other instanceof AddNullMarkedPackageInfo that
                && Objects.equals(packages, that.packages)
                && Objects.equals(spotBugsDefaults, that.spotBugsDefaults);
    }

    @Override
    public int hashCode() {
        return Objects.hash(packages, spotBugsDefaults);
    }
}
