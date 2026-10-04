package cn.edu.jxnu.civicsresources.basedata;

import java.util.List;

public record BaseDataPage(List<BaseDataView> items, long total, int page, int size) {
}
