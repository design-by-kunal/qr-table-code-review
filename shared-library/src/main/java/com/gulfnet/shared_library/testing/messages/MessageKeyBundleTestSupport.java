package com.gulfnet.shared_library.testing.messages;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Shared helpers for module-local message bundle tests (keys in {@code messages_*.properties}
 * vs {@code MessageKeys} / {@code getMessage("...")} / literal scans). Intended for test code only.
 */
public final class MessageKeyBundleTestSupport {

    public static final String MESSAGES_EN = "messages_en.properties";
    public static final String MESSAGES_JA = "messages_ja.properties";
    public static final String MESSAGES_TH = "messages_th.properties";

    private static final Pattern MESSAGE_KEY_PATTERN =
            Pattern.compile("getMessage\\(\\s*\"([^\"]+)\"");
    private static final Pattern PACKAGE_PATTERN =
            Pattern.compile("^\\s*package\\s+([\\w\\.]+);\\s*$", Pattern.MULTILINE);
    private static final Pattern CLASS_DECL_PATTERN =
            Pattern.compile("\\b(class|public\\s+class|private\\s+class|protected\\s+class)\\s+([A-Za-z_]\\w*)\\b");

    private MessageKeyBundleTestSupport() {
    }

    /**
     * @param missingTranslationsHeader first block of the failure message, typically ending with blank lines
     * @return {@code null} if all referenced keys exist in EN/JA/TH; otherwise a message suitable for {@code Assertions#fail}
     */
    public static String validateUsedKeysExistInAllBundles(Class<?> anchorClass, String missingTranslationsHeader) {
        Set<String> keysFromEnBundle = loadPropertyKeys(MESSAGES_EN, anchorClass);
        Set<String> keysFromJaBundle = loadPropertyKeys(MESSAGES_JA, anchorClass);
        Set<String> keysFromThBundle = loadPropertyKeys(MESSAGES_TH, anchorClass);

        Set<String> keysFromConstants = collectKeysFromMessageKeysConstants(anchorClass);
        Set<String> keysFromCode = collectKeysFromGetMessageCalls(anchorClass);

        Set<String> allKeys = new HashSet<>();
        allKeys.addAll(keysFromConstants);
        allKeys.addAll(keysFromCode);

        Set<String> missingInEn = treeSortedMissing(allKeys, keysFromEnBundle);
        Set<String> missingInJa = treeSortedMissing(allKeys, keysFromJaBundle);
        Set<String> missingInTh = treeSortedMissing(allKeys, keysFromThBundle);

        if (missingInEn.isEmpty() && missingInJa.isEmpty() && missingInTh.isEmpty()) {
            return null;
        }
        return buildMissingTranslationsMessage(missingInEn, missingInJa, missingInTh, missingTranslationsHeader);
    }

    /**
     * Runs {@link #validateUsedKeysExistInAllBundles(Class, String)} and throws {@link AssertionError} when keys are missing
     * (same outcome as {@code Assertions.fail} for JUnit test failures).
     */
    public static void assertUsedKeysExistInAllBundles(Class<?> anchorClass, String missingTranslationsHeader) {
        String failure = validateUsedKeysExistInAllBundles(anchorClass, missingTranslationsHeader);
        if (failure != null) {
            throw new AssertionError(failure);
        }
    }

    /**
     * Same as {@link #detectUnusedBundleKeysViolation(Class, Predicate)} with no ignored unused-key patterns.
     */
    public static String detectUnusedBundleKeysViolation(Class<?> anchorClass) {
        return detectUnusedBundleKeysViolation(anchorClass, k -> true);
    }

    /**
     * @param reportUnusedIfPresent for each bundle key that is unused, return {@code true} to include it in the failure output
     * @return {@code null} if there are no unused keys to report; otherwise a message suitable for {@code Assertions#fail}
     */
    public static String detectUnusedBundleKeysViolation(Class<?> anchorClass, Predicate<String> reportUnusedIfPresent) {
        Set<String> keysFromEn = loadPropertyKeys(MESSAGES_EN, anchorClass);
        Set<String> keysFromJa = loadPropertyKeys(MESSAGES_JA, anchorClass);
        Set<String> keysFromTh = loadPropertyKeys(MESSAGES_TH, anchorClass);

        Set<String> keysFromConstants = collectKeysFromMessageKeysConstants(anchorClass);
        Set<String> keysFromCode = collectKeysFromGetMessageCalls(anchorClass);

        Set<String> usedKeys = new HashSet<>();
        usedKeys.addAll(keysFromConstants);
        usedKeys.addAll(keysFromCode);

        Set<String> allBundleKeys = new HashSet<>();
        allBundleKeys.addAll(keysFromEn);
        allBundleKeys.addAll(keysFromJa);
        allBundleKeys.addAll(keysFromTh);

        usedKeys.addAll(collectKeysFromLiteralOccurrences(anchorClass, allBundleKeys));

        Set<String> unusedKeys = allBundleKeys.stream()
                .filter(k -> !usedKeys.contains(k))
                .filter(reportUnusedIfPresent)
                .collect(Collectors.toCollection(TreeSet::new));

        if (unusedKeys.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Unused message keys (present in properties but not used in code, MessageKeys constants, or scanned literals):\n\n");
        for (String key : unusedKeys) {
            sb.append(key).append('\n');
        }
        return sb.toString();
    }

    /**
     * Runs {@link #detectUnusedBundleKeysViolation(Class)} and throws {@link AssertionError} when unused keys are found.
     */
    public static void assertNoUnusedBundleKeysViolation(Class<?> anchorClass) {
        String failure = detectUnusedBundleKeysViolation(anchorClass);
        if (failure != null) {
            throw new AssertionError(failure);
        }
    }

    /**
     * Runs {@link #detectUnusedBundleKeysViolation(Class, Predicate)} and throws {@link AssertionError} when unused keys are found.
     */
    public static void assertNoUnusedBundleKeysViolation(Class<?> anchorClass, Predicate<String> reportUnusedIfPresent) {
        String failure = detectUnusedBundleKeysViolation(anchorClass, reportUnusedIfPresent);
        if (failure != null) {
            throw new AssertionError(failure);
        }
    }

    /**
     * Loads a classpath properties file as UTF-8 and returns all defined property keys.
     *
     * @param fileName   classpath resource path (e.g. messages bundle)
     * @param anchorClass class whose {@link ClassLoader} resolves the resource
     * @return distinct keys from the properties file
     */
    public static Set<String> loadPropertyKeys(String fileName, Class<?> anchorClass) {
        ClassLoader classLoader = anchorClass.getClassLoader();
        URL resourceUrl = classLoader.getResource(fileName);
        if (resourceUrl == null) {
            throw new IllegalStateException("Missing resource on classpath: " + fileName);
        }

        Properties props = new Properties();
        try (InputStream inputStream = classLoader.getResourceAsStream(fileName);
             InputStreamReader reader =
                     new InputStreamReader(Objects.requireNonNull(inputStream), StandardCharsets.UTF_8)) {
            props.load(reader);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load properties: " + fileName, e);
        }

        return props.stringPropertyNames();
    }

    private static Set<String> treeSortedMissing(Set<String> allKeys, Set<String> availableKeys) {
        return allKeys.stream()
                .filter(k -> !availableKeys.contains(k))
                .collect(Collectors.toCollection(TreeSet::new));
    }

    /**
     * Formats a human-readable report of keys missing from EN, JA, or TH bundles.
     *
     * @param missingInEn keys absent from the English bundle
     * @param missingInJa keys absent from the Japanese bundle
     * @param missingInTh keys absent from the Thai bundle
     * @param missingTranslationsHeader first line of the report (title/prefix)
     * @return multi-line message listing missing keys per locale
     */
    private static String buildMissingTranslationsMessage(
            Set<String> missingInEn,
            Set<String> missingInJa,
            Set<String> missingInTh,
            String missingTranslationsHeader) {
        StringBuilder sb = new StringBuilder();
        sb.append(missingTranslationsHeader);

        sb.append("Missing in EN:\n");
        appendKeys(sb, missingInEn);

        sb.append("\n\nMissing in JA:\n");
        appendKeys(sb, missingInJa);

        sb.append("\n\nMissing in TH:\n");
        appendKeys(sb, missingInTh);

        return sb.toString();
    }

    private static void appendKeys(StringBuilder sb, Set<String> keys) {
        for (String key : keys) {
            sb.append(key).append('\n');
        }
    }

    /**
     * Walks {@code src/main/java} under the anchor module, loads nested {@code MessageKeys} classes, and collects
     * their public static string constants that look like message keys (contain a dot).
     *
     * @param anchorClass test anchor class used for classpath and module resolution
     * @return discovered keys, or empty when source tree is not available
     */
    private static Set<String> collectKeysFromMessageKeysConstants(Class<?> anchorClass) {
        Path srcMainJava = resolveSrcMainJavaDir(anchorClass);
        if (!Files.isDirectory(srcMainJava)) {
            return Set.of();
        }

        Set<String> keys = new HashSet<>();
        Pattern messageKeysClassPattern =
                Pattern.compile("\\bclass\\s+MessageKeys\\b|\\bstatic\\s+class\\s+MessageKeys\\b");

        try (Stream<Path> pathStream = Files.walk(srcMainJava)) {
            for (Path javaFile : pathStream
                    .filter(p -> p.toString().endsWith(".java"))
                    .toList()) {
                collectKeysFromMessageKeysSourceFile(javaFile, messageKeysClassPattern, keys, anchorClass);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed while scanning for MessageKeys constants.", e);
        }

        return keys;
    }

    /**
     * If {@code javaFile} declares a nested {@code MessageKeys} class, reflects that class and merges its string keys.
     *
     * @param javaFile                 Java source path under scan
     * @param messageKeysClassPattern pattern matching {@code MessageKeys} class declarations
     * @param keys                     output set to merge keys into
     * @param anchorClass              class loader anchor for {@code Class.forName}
     */
    private static void collectKeysFromMessageKeysSourceFile(
            Path javaFile, Pattern messageKeysClassPattern, Set<String> keys, Class<?> anchorClass) {
        try {
            String content = Files.readString(javaFile, StandardCharsets.UTF_8);
            if (!messageKeysClassPattern.matcher(content).find()) {
                return;
            }
            String packageName = extractPackageName(content);
            if (packageName == null || packageName.isBlank()) {
                return;
            }
            String outerClassName = findOuterClassNameBeforeMessageKeys(content);
            if (outerClassName == null || outerClassName.isBlank()) {
                return;
            }
            String binaryName = packageName + "." + outerClassName + "$MessageKeys";
            appendKeysFromMessageKeysClass(binaryName, keys, anchorClass);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read: " + javaFile, e);
        }
    }

    private static void appendKeysFromMessageKeysClass(String binaryName, Set<String> keys, Class<?> anchorClass) {
        try {
            Class<?> messageKeysClass = Class.forName(binaryName, false, anchorClass.getClassLoader());
            for (Field field : messageKeysClass.getDeclaredFields()) {
                maybeAddKeyFromStaticStringField(field, keys);
            }
        } catch (ClassNotFoundException ignored) {
            // Ignore classes that cannot be loaded
        }
    }

    /**
     * Adds {@code field}'s value to {@code keys} when it is a public static {@link String} containing a dot.
     *
     * @param field static field on {@code MessageKeys}
     * @param keys  collector for key strings
     */
    private static void maybeAddKeyFromStaticStringField(Field field, Set<String> keys) {
        if (!Modifier.isStatic(field.getModifiers()) || !field.getType().equals(String.class)) {
            return;
        }
        try {
            if (!field.canAccess(null) && !field.trySetAccessible()) {
                return;
            }
        } catch (SecurityException | InaccessibleObjectException ignored) {
            return;
        }
        final Object rawValue;
        try {
            rawValue = field.get(null);
        } catch (IllegalAccessException ignored) {
            return;
        }
        if (rawValue instanceof String value && value.contains(".")) {
            keys.add(value);
        }
    }

    private static String extractPackageName(String javaSource) {
        Matcher matcher = PACKAGE_PATTERN.matcher(javaSource);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * Returns the simple name of the last outer class declared before the nested {@code MessageKeys} type in source.
     *
     * @param javaSource full Java source text
     * @return outer class name, or {@code null} if {@code MessageKeys} is not found
     */
    private static String findOuterClassNameBeforeMessageKeys(String javaSource) {
        int messageKeysIndex = javaSource.indexOf("class MessageKeys");
        if (messageKeysIndex < 0) {
            messageKeysIndex = javaSource.indexOf("static class MessageKeys");
        }
        if (messageKeysIndex < 0) {
            return null;
        }

        String prefix = javaSource.substring(0, messageKeysIndex);
        Matcher classMatcher = CLASS_DECL_PATTERN.matcher(prefix);

        String lastClassName = null;
        while (classMatcher.find()) {
            lastClassName = classMatcher.group(2);
        }
        return lastClassName;
    }

    /**
     * Scans Java sources for string literals passed as the first argument to {@code getMessage("...")}-style calls.
     *
     * @param anchorClass test anchor for resolving {@code src/main/java}
     * @return keys inferred from direct string arguments
     */
    private static Set<String> collectKeysFromGetMessageCalls(Class<?> anchorClass) {
        Path srcMainJava = resolveSrcMainJavaDir(anchorClass);
        if (!Files.isDirectory(srcMainJava)) {
            return Set.of();
        }

        Set<String> keys = new HashSet<>();

        try (Stream<Path> pathStream = Files.walk(srcMainJava)) {
            for (Path javaFile : pathStream
                    .filter(p -> p.toString().endsWith(".java"))
                    .toList()) {

                String content = Files.readString(javaFile, StandardCharsets.UTF_8);
                Matcher matcher = MESSAGE_KEY_PATTERN.matcher(content);
                while (matcher.find()) {
                    String key = matcher.group(1);
                    if (isDirectStringArgument(content, matcher.end())) {
                        keys.add(key);
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed while scanning for getMessage(\"...\") usages.", e);
        }

        return keys;
    }

    private static boolean isDirectStringArgument(String content, int afterQuoteIndex) {
        int idx = afterQuoteIndex;
        while (idx < content.length()) {
            char ch = content.charAt(idx);
            if (!Character.isWhitespace(ch)) {
                return ch == ',' || ch == ')';
            }
            idx++;
        }
        return false;
    }

    /**
     * Finds which bundle keys appear as substrings in source and resource files under the module (and shared-library).
     *
     * @param anchorClass     used to locate module roots
     * @param allBundleKeys   keys to search for
     * @return subset of {@code allBundleKeys} that occur in scanned text files
     */
    private static Set<String> collectKeysFromLiteralOccurrences(Class<?> anchorClass, Set<String> allBundleKeys) {
        Path moduleRoot = resolveModuleRoot(anchorClass);
        if (moduleRoot == null || !Files.isDirectory(moduleRoot)) {
            return Set.of();
        }

        ArrayList<Path> scanRoots = new ArrayList<>();
        Path srcMainJava = moduleRoot.resolve(Paths.get("src", "main", "java"));
        Path srcMainResources = moduleRoot.resolve(Paths.get("src", "main", "resources"));

        if (Files.isDirectory(srcMainJava)) {
            scanRoots.add(srcMainJava);
        }
        if (Files.isDirectory(srcMainResources)) {
            scanRoots.add(srcMainResources);
        }

        Path repoRoot = moduleRoot.getParent();
        if (repoRoot != null && Files.isDirectory(repoRoot)) {
            Path sharedLibJava = repoRoot.resolve(Paths.get("shared-library", "src", "main", "java"));
            Path sharedLibResources = repoRoot.resolve(Paths.get("shared-library", "src", "main", "resources"));
            if (Files.isDirectory(sharedLibJava)) {
                scanRoots.add(sharedLibJava);
            }
            if (Files.isDirectory(sharedLibResources)) {
                scanRoots.add(sharedLibResources);
            }
        }

        if (scanRoots.isEmpty()) {
            return Set.of();
        }

        String haystack = buildSearchHaystack(scanRoots);
        if (haystack.isEmpty()) {
            return Set.of();
        }

        Set<String> found = new HashSet<>();
        for (String key : allBundleKeys) {
            if (haystack.contains(key)) {
                found.add(key);
            }
        }
        return found;
    }

    private static String buildSearchHaystack(ArrayList<Path> scanRoots) {
        StringBuilder sb = new StringBuilder(1024 * 1024);

        for (Path root : scanRoots) {
            appendHaystackFromScanRoot(root, sb);
        }

        return sb.toString();
    }

    /**
     * Concatenates text contents of eligible files under {@code root} into {@code sb} for substring key detection.
     *
     * @param root directory to walk (typically {@code src/main/java} or {@code src/main/resources})
     * @param sb   aggregated haystack builder
     */
    private static void appendHaystackFromScanRoot(Path root, StringBuilder sb) {
        try (Stream<Path> pathStream = Files.walk(root)) {
            for (Path file : pathStream
                    .filter(Files::isRegularFile)
                    .filter(p -> !isInIgnoredDirectory(p))
                    .filter(MessageKeyBundleTestSupport::shouldScanFileForLiterals)
                    .toList()) {
                String fileName = file.getFileName().toString();
                if (!MESSAGES_EN.equals(fileName) && !MESSAGES_JA.equals(fileName) && !MESSAGES_TH.equals(fileName)) {
                    appendFileContentToHaystack(file, sb);
                }
            }
        } catch (IOException ignored) {
            // ignore directory traversal failures
        }
    }

    private static void appendFileContentToHaystack(Path file, StringBuilder sb) {
        try {
            sb.append(Files.readString(file, StandardCharsets.UTF_8)).append('\n');
        } catch (IOException ignored) {
            // ignore unreadable files
        }
    }

    private static boolean isInIgnoredDirectory(Path path) {
        String p = path.toString().replace('\\', '/');
        return p.contains("/build/")
                || p.contains("/out/")
                || p.contains("/target/")
                || p.contains("/.gradle/")
                || p.contains("/.idea/")
                || p.contains("/node_modules/");
    }

    /**
     * Whether {@code file} should be included when building the literal haystack (by extension).
     *
     * @param file candidate file path
     * @return {@code true} for common text-like source and config formats
     */
    private static boolean shouldScanFileForLiterals(Path file) {
        String name = file.getFileName().toString().toLowerCase();

        return name.endsWith(".java")
                || name.endsWith(".properties")
                || name.endsWith(".yml")
                || name.endsWith(".yaml")
                || name.endsWith(".xml")
                || name.endsWith(".json")
                || name.endsWith(".html")
                || name.endsWith(".htm")
                || name.endsWith(".ftl")
                || name.endsWith(".mustache")
                || name.endsWith(".txt")
                || name.endsWith(".md");
    }

    /**
     * Resolves the anchor module's {@code src/main/java} directory from the {@code messages_en.properties} resource URI.
     *
     * @param anchorClass class whose loader locates the English messages bundle
     * @return absolute or relative path to {@code src/main/java}, with a classpath fallback when unknown
     */
    public static Path resolveSrcMainJavaDir(Class<?> anchorClass) {
        ClassLoader classLoader = anchorClass.getClassLoader();
        URL resourceUrl = classLoader.getResource(MESSAGES_EN);
        if (resourceUrl == null) {
            return Paths.get("src", "main", "java");
        }

        try {
            Path messagesFilePath = Paths.get(resourceUrl.toURI());
            Path moduleRoot = messagesFilePath.getParent()
                    .getParent()
                    .getParent()
                    .getParent();
            return moduleRoot.resolve(Paths.get("src", "main", "java"));
        } catch (URISyntaxException e) {
            return Paths.get("src", "main", "java");
        }
    }

    /**
     * Resolves the Gradle/Maven module root containing {@code messages_en.properties} for {@code anchorClass}.
     *
     * @param anchorClass class whose loader locates the English messages bundle
     * @return module root path, or {@code Paths.get(".")} when the resource URI cannot be resolved
     */
    private static Path resolveModuleRoot(Class<?> anchorClass) {
        ClassLoader classLoader = anchorClass.getClassLoader();
        URL resourceUrl = classLoader.getResource(MESSAGES_EN);
        if (resourceUrl == null) {
            return Paths.get(".");
        }

        try {
            Path messagesFilePath = Paths.get(resourceUrl.toURI());
            return messagesFilePath.getParent()
                    .getParent()
                    .getParent()
                    .getParent();
        } catch (URISyntaxException e) {
            return Paths.get(".");
        }
    }
}
