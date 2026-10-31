package cn.edu.jxnu.civicsresources.resource;
public final class ResourceVisibility {
 private ResourceVisibility(){}
 public static final String PUBLISHED="r.status='APPROVED' AND r.deleted_at IS NULL AND r.published_at IS NOT NULL AND r.withdrawn_at IS NULL";
}
