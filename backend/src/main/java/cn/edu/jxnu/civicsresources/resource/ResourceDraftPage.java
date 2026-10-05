package cn.edu.jxnu.civicsresources.resource;

import java.util.List;

public record ResourceDraftPage(List<ResourceDraftView> items, long total, int page, int size) { }
