package com.panloader.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.panloader.api.ModMetadata;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class MetadataParser {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String METADATA_ENTRY = "panloader.mod.json";

    public static ModMetadata parseFromJar(JarFile jarFile) throws IOException {
        JarEntry entry = jarFile.getJarEntry(METADATA_ENTRY);
        if (entry == null) {
            return null;
        }
        String json = readJson(jarFile, entry);
        return parse(json);
    }

    public static ModMetadata parse(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();

        ModMetadata metadata = new ModMetadata();
        metadata.setId(getString(root, "id", "unknown"));
        metadata.setVersion(getString(root, "version", "0.0.0"));
        metadata.setName(getString(root, "name", metadata.getId()));
        metadata.setDescription(getString(root, "description", ""));
        metadata.setEntrypoint(getString(root, "entrypoint", ""));

        if (root.has("author")) {
            metadata.setAuthor(root.get("author").getAsString());
        }
        if (root.has("dependencies")) {
            List<String> deps = new ArrayList<>();
            root.getAsJsonArray("dependencies").forEach(e -> deps.add(e.getAsString()));
            metadata.setDependencies(deps);
        }

        return metadata;
    }

    private static String getString(JsonObject obj, String key, String defaultVal) {
        if (obj.has(key)) {
            return obj.get(key).getAsString();
        }
        return defaultVal;
    }

    private static String readJson(JarFile jarFile, JarEntry entry) throws IOException {
        try (InputStream is = jarFile.getInputStream(entry);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            char[] buffer = new char[8192];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                sb.append(buffer, 0, read);
            }
            return sb.toString();
        }
    }
}
