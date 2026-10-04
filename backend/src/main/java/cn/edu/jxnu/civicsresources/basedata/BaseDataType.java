package cn.edu.jxnu.civicsresources.basedata;

import cn.edu.jxnu.civicsresources.common.BusinessException;
import org.springframework.http.HttpStatus;

/** SQL identifiers are selected only from this closed allow-list. */
public enum BaseDataType {
    COURSE("courses", "course", "课程", 120),
    ELEMENT("ideological-elements", "ideological_element", "课程思政元素", 100),
    CATEGORY("resource-categories", "resource_category", "资源分类", 80);

    private final String path;
    private final String table;
    private final String label;
    private final int nameLimit;

    BaseDataType(String path, String table, String label, int nameLimit) {
        this.path = path;
        this.table = table;
        this.label = label;
        this.nameLimit = nameLimit;
    }

    public String table() { return table; }
    public String label() { return label; }
    public int nameLimit() { return nameLimit; }
    public boolean isCourse() { return this == COURSE; }
    public String uniqueColumn() { return isCourse() ? "course_code" : "name"; }

    public static BaseDataType fromPath(String path) {
        for (BaseDataType type : values()) {
            if (type.path.equals(path)) return type;
        }
        throw new BusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND", "基础数据类型不存在");
    }
}
