package cn.edu.jxnu.civicsresources.resource;

public record StoredResourceFile(String key, String originalName, String mimeType, long sizeBytes) { }
