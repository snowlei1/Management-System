package cn.edu.jxnu.civicsresources.resource;

import java.util.List;

// Only editable metadata. Creator and lifecycle state are never client inputs.
public record ResourceDraftRequest(String title, String description, Long courseId,
        Long categoryId, List<Long> elementIds) { }
