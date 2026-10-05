package cn.edu.jxnu.civicsresources.resource;

import java.util.List;

public record PublishedResourcePage(List<PublishedResourceView> items, long total, int page, int size) { }
