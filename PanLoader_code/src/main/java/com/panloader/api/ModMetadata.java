package com.panloader.api;

import java.util.ArrayList;
import java.util.List;

public class ModMetadata {

    private String id;
    private String version;
    private String name;
    private String description;
    private String entrypoint;
    private String clientEntrypoint;
    private String serverEntrypoint;
    private String author;
    private List<String> dependencies = new ArrayList<>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getEntrypoint() {
        return entrypoint;
    }

    public void setEntrypoint(String entrypoint) {
        this.entrypoint = entrypoint;
    }

    public String getClientEntrypoint() {
        return clientEntrypoint;
    }

    public void setClientEntrypoint(String clientEntrypoint) {
        this.clientEntrypoint = clientEntrypoint;
    }

    public String getServerEntrypoint() {
        return serverEntrypoint;
    }

    public void setServerEntrypoint(String serverEntrypoint) {
        this.serverEntrypoint = serverEntrypoint;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public List<String> getDependencies() {
        return dependencies;
    }

    public void setDependencies(List<String> dependencies) {
        this.dependencies = dependencies != null ? dependencies : new ArrayList<>();
    }

    @Override
    public String toString() {
        return "ModMetadata{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", version='" + version + '\'' +
                ", entrypoint='" + entrypoint + '\'' +
                ", clientEntrypoint='" + clientEntrypoint + '\'' +
                ", serverEntrypoint='" + serverEntrypoint + '\'' +
                '}';
    }
}
