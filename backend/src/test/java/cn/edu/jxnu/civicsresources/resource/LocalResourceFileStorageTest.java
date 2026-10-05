package cn.edu.jxnu.civicsresources.resource;

import static org.junit.jupiter.api.Assertions.*;
import cn.edu.jxnu.civicsresources.common.BusinessException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import java.util.zip.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

class LocalResourceFileStorageTest {
    @TempDir Path directory;
    LocalResourceFileStorage storage() {
        return new LocalResourceFileStorage(new ResourceStorageProperties(directory.toString(), DataSize.ofKilobytes(1),
                List.of("pdf","doc","docx","ppt","pptx","xls","xlsx","jpg","jpeg","png")));
    }
    static byte[] pdf() { return "%PDF-1.4\n1 0 obj <<>> endobj\n%%EOF\n".getBytes(StandardCharsets.US_ASCII); }
    static MockMultipartFile file(String name, String mime, byte[] bytes) { return new MockMultipartFile("file", name, mime, bytes); }
    static byte[] docx() throws IOException { return zip("word/document.xml", "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml", false); }
    static byte[] zip(String root, String type, boolean macro) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zip.write(("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Override PartName=\"/" + root + "\" ContentType=\"" + type + "\"/></Types>").getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
            zip.putNextEntry(new ZipEntry(root)); zip.write("<document/>".getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
            if (macro) { zip.putNextEntry(new ZipEntry("word/vbaProject.bin")); zip.write(1); zip.closeEntry(); }
        }
        return bytes.toByteArray();
    }
    @Test void uniqueStorageKeysAndOriginalChineseNames() throws IOException {
        var s = storage(); var a = s.save(file("课程 思政 (案例)#1.PDF", "application/pdf", pdf()));
        var b = s.save(file("课程 思政 (案例)#1.PDF", "application/pdf", pdf()));
        assertNotEquals(a.key(), b.key()); assertEquals("课程 思政 (案例)#1.PDF", a.originalName());
        assertArrayEquals(pdf(), Files.readAllBytes(directory.resolve(a.key())));
        s.discard(a.key()); assertFalse(Files.exists(directory.resolve(a.key()))); assertTrue(Files.exists(directory.resolve(b.key())));
    }
    @ParameterizedTest @ValueSource(strings = {"../a.pdf", "..\\a.pdf", "/a.pdf", "C:\\a.pdf", "a\r\n.pdf", "a\u0000.pdf", ".pdf", "a.pdf.", "a\u202e.pdf"})
    void unsafeNameRejected(String name) { assertEquals(HttpStatus.BAD_REQUEST, assertThrows(BusinessException.class, () -> storage().save(file(name, "application/pdf", pdf()))).status()); }
    @Test void validDocx() throws IOException { assertEquals("a.docx", storage().save(file("a.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docx())).originalName()); }
    @Test void rejectsWrongOfficePackageAndMacros() throws IOException {
        var s = storage(); var invalid = zip("xl/workbook.xml", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml", false);
        assertThrows(BusinessException.class, () -> s.save(file("a.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", invalid)));
        var macro = zip("word/document.xml", "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml", true);
        assertThrows(BusinessException.class, () -> s.save(file("a.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", macro)));
        try (var list = Files.list(directory)) { assertEquals(0, list.count()); }
    }
    @Test void oversize() { assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, assertThrows(BusinessException.class, () -> storage().save(file("a.pdf", "application/pdf", new byte[1025]))).status()); }
    @Test void empty() { assertThrows(BusinessException.class, () -> storage().save(file("a.pdf", "application/pdf", new byte[0]))); }
    @Test void invalidExtension() { assertThrows(BusinessException.class, () -> storage().save(file("a.exe", "application/pdf", pdf()))); }
    @Test void mismatchedMime() { assertThrows(BusinessException.class, () -> storage().save(file("a.pdf", "image/png", pdf()))); }
    @Test void invalidContentCleanup() throws IOException {
        assertThrows(BusinessException.class, () -> storage().save(file("a.pdf", "application/pdf", new byte[]{1,2,3})));
        try (var list = Files.list(directory)) { assertEquals(0, list.count()); }
    }
    @Test void cleanupRejectsClientPath() { assertThrows(IllegalArgumentException.class, () -> storage().discard("../a.pdf")); }
    @Test void streamSizeCannotBypassLimit() throws IOException {
        var malicious = new MockMultipartFile("file", "a.pdf", "application/pdf", new byte[1025]) { @Override public long getSize() { return 1; } };
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, assertThrows(BusinessException.class, () -> storage().save(malicious)).status());
        try (var list = Files.list(directory)) { assertEquals(0, list.count()); }
    }
}
