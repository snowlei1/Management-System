package cn.edu.jxnu.civicsresources.resource;

import static org.junit.jupiter.api.Assertions.*;
import cn.edu.jxnu.civicsresources.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ResourceSearchOptionsTest {
    @Test void defaultAndFixedSort() {
        assertEquals(ResourceSearchOptions.defaults(), ResourceSearchOptions.parse(null,null,null,null));
        assertEquals("r.published_at ASC,r.id ASC", ResourceSearchOptions.parse("PDF","OLDEST",null,null).orderBy());
        assertEquals("r.title ASC,r.id DESC", ResourceSearchOptions.parse("IMAGE","TITLE",null,null).orderBy());
        assertEquals(1, ResourceSearchOptions.parse("OFFICE","NEWEST","2026-10-01","2026-10-01").from().getDayOfMonth());
    }
    @ParameterizedTest @CsvSource({"VIDEO,NEWEST,,", "ALL,drop table,,", "ALL,NEWEST,2026-02-30,", "ALL,NEWEST,2026-10-20,2026-10-01", "ALL,NEWEST,,9999-01-01"})
    void invalidOptions(String format,String sort,String from,String to) {
        assertEquals(400,assertThrows(BusinessException.class,()->ResourceSearchOptions.parse(format,sort,from,to)).status().value());
    }
}
