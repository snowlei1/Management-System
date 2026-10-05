package cn.edu.jxnu.civicsresources.resource;

import cn.edu.jxnu.civicsresources.common.BusinessException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.Normalizer;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class LocalResourceFileStorage {
    private static final Logger log = LoggerFactory.getLogger(LocalResourceFileStorage.class);
    private static final Map<String, String> MIME = Map.ofEntries(
            Map.entry("pdf", "application/pdf"), Map.entry("doc", "application/msword"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("ppt", "application/vnd.ms-powerpoint"),
            Map.entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
            Map.entry("xls", "application/vnd.ms-excel"),
            Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry("jpg", "image/jpeg"), Map.entry("jpeg", "image/jpeg"), Map.entry("png", "image/png"));
    private static final Map<String, String> OOXML_ROOT = Map.of(
            "docx", "word/document.xml", "pptx", "ppt/presentation.xml", "xlsx", "xl/workbook.xml");
    private static final Map<String, String> OOXML_TYPE = Map.of(
            "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml",
            "pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml",
            "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml");
    private final Path root;
    private final long maxSize;
    private final Set<String> allowed;

    public LocalResourceFileStorage(ResourceStorageProperties properties) {
        root = Path.of(properties.directory()).toAbsolutePath().normalize();
        maxSize = properties.maxSize().toBytes();
        allowed = Set.copyOf(properties.allowedExtensions());
        if (maxSize <= 0 || !MIME.keySet().containsAll(allowed)) {
            throw new IllegalArgumentException("资源上传配置不正确");
        }
    }

    public StoredResourceFile save(MultipartFile file) {
        if (file == null || file.isEmpty()) throw invalid("请选择非空资源文件");
        if (file.getSize() > maxSize) throw tooLarge();
        String original = safeOriginalName(file.getOriginalFilename());
        String ext = original.substring(original.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        if (!allowed.contains(ext)) throw invalid("不支持该文件扩展名");
        String mime = MIME.get(ext);
        String suppliedMime = file.getContentType();
        if (suppliedMime == null || !mime.equalsIgnoreCase(suppliedMime.split(";", 2)[0].strip())) {
            throw invalid("文件 Content-Type 与允许类型不匹配");
        }
        String key = UUID.randomUUID() + "." + ext;
        Path target = root.resolve(key);
        boolean created = false;
        try {
            Files.createDirectories(root);
            if (Files.isSymbolicLink(root)) throw new IOException("Invalid storage directory");
            long size = 0;
            try (InputStream input = file.getInputStream();
                    OutputStream output = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW)) {
                created = true;
                byte[] buffer = new byte[8192];
                int n;
                while ((n = input.read(buffer)) != -1) {
                    size += n;
                    if (size > maxSize) throw tooLarge();
                    output.write(buffer, 0, n);
                }
            }
            if (size == 0) throw invalid("请选择非空资源文件");
            validateContent(target, ext);
            return new StoredResourceFile(key, original, mime, size);
        } catch (BusinessException exception) {
            if (created) discard(key);
            throw exception;
        } catch (IOException exception) {
            if (created) discard(key);
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "FILE_STORAGE_UNAVAILABLE", "文件保存失败，请稍后重试");
        }
    }

    // Only server-generated flat keys are ever resolved; callers cannot supply a client path.
    public void verify(TeachingResource resource) { readVerified(resource); }

    public byte[] readVerified(TeachingResource resource) {
        String key = resource.storageKey();
        if (key == null || !key.matches("[a-f0-9-]{36}\\.[a-z0-9]+")) throw invalid("资源文件记录无效，请联系管理员");
        String original = safeOriginalName(resource.originalName());
        String ext = key.substring(key.lastIndexOf('.') + 1);
        String originalExt = original.substring(original.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        if (!allowed.contains(ext) || !ext.equals(originalExt) || !Objects.equals(MIME.get(ext), resource.mimeType())
                || resource.sizeBytes() <= 0 || resource.sizeBytes() > maxSize) throw invalid("资源文件记录无效");
        Path path = root.resolve(key).normalize();
        try {
            if (!path.startsWith(root) || Files.isSymbolicLink(root) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                    || !Files.isReadable(path) || !path.toRealPath().startsWith(root.toRealPath())
                    || Files.size(path) != resource.sizeBytes()) throw invalid("资源文件不存在、不可读或大小不一致，请重新上传");
            validateContent(path, ext);
            byte[] bytes = Files.readAllBytes(path);
            if (bytes.length != resource.sizeBytes()) throw invalid("资源文件大小不一致，请重新上传");
            return bytes;
        } catch (IOException exception) { throw invalid("资源文件暂不可读，请稍后重试"); }
    }

    public void discard(String key) {
        if (key == null || !key.matches("[a-f0-9-]{36}\\.[a-z0-9]+")) {
            throw new IllegalArgumentException("Invalid internal storage key");
        }
        try {
            Files.deleteIfExists(root.resolve(key));
        } catch (IOException exception) {
            // Do not turn a committed DB operation into a misleading failure response.
            log.error("Resource file cleanup failed for generated key {}. Manual reconciliation required.", key);
        }
    }

    static String safeOriginalName(String name) {
        if (name == null) throw invalid("文件名不正确");
        String normalized = Normalizer.normalize(name, Normalizer.Form.NFC).strip();
        if (normalized.isBlank() || normalized.length() > 255 || normalized.lastIndexOf('.') <= 0
                || normalized.endsWith(".") || normalized.contains("/") || normalized.contains("\\")
                || normalized.contains(":") || normalized.codePoints().anyMatch(c -> Character.isISOControl(c)
                    || Character.getType(c) == Character.FORMAT)) throw invalid("文件名不正确，不允许路径或控制字符");
        return normalized;
    }

    private static void validateContent(Path path, String ext) throws IOException {
        byte[] bytes = Files.readAllBytes(path); // Hard bounded by configured maximum before reading.
        boolean valid = switch (ext) {
            case "pdf" -> starts(bytes, "%PDF-".getBytes(StandardCharsets.US_ASCII))
                    && new String(bytes, Math.max(0, bytes.length - 1024), Math.min(1024, bytes.length),
                            StandardCharsets.ISO_8859_1).contains("%%EOF");
            case "png" -> starts(bytes, new byte[]{(byte)137, 80, 78, 71, 13, 10, 26, 10})
                    && bytes.length > 24;
            case "jpg", "jpeg" -> bytes.length > 4 && (bytes[0] & 255) == 255
                    && (bytes[1] & 255) == 216 && (bytes[2] & 255) == 255
                    && (bytes[bytes.length - 2] & 255) == 255 && (bytes[bytes.length - 1] & 255) == 217;
            case "doc", "ppt", "xls" -> validOle(bytes, ext);
            case "docx", "pptx", "xlsx" -> validOoxml(bytes, ext);
            default -> false;
        };
        if (!valid) throw invalid("文件内容特征与声明类型不匹配，或文档结构不受支持");
    }

    private static boolean validOle(byte[] bytes, String ext) {
        if (bytes.length < 512 || !starts(bytes, new byte[]{(byte)208,(byte)207,17,(byte)224,(byte)161,(byte)177,26,(byte)225})
                || (bytes[28] & 255) != 254 || (bytes[29] & 255) != 255) return false;
        // Legacy Office compound-file stream name check; not a complete document parser or antivirus.
        String contents = new String(bytes, StandardCharsets.UTF_16LE);
        return switch (ext) {
            case "doc" -> contents.contains("WordDocument\0");
            case "ppt" -> contents.contains("PowerPoint Document\0");
            case "xls" -> contents.contains("Workbook\0") || contents.contains("Book\0");
            default -> false;
        };
    }

    private static boolean validOoxml(byte[] bytes, String ext) {
        if (!starts(bytes, new byte[]{80,75,3,4})) return false;
        boolean rootSeen = false;
        byte[] types = null;
        Set<String> names = new HashSet<>();
        long expanded = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (!names.add(name) || names.size() > 2000 || name.startsWith("/") || name.contains("..")
                        || name.contains("\\") || name.toLowerCase(Locale.ROOT).contains("vbaproject")) return false;
                ByteArrayOutputStream captured = name.equals("[Content_Types].xml") ? new ByteArrayOutputStream() : null;
                int n;
                while ((n = zip.read(buffer)) != -1) {
                    expanded += n;
                    if (expanded > 100L * 1024 * 1024) return false;
                    if (captured != null) {
                        if (captured.size() + n > 1024 * 1024) return false;
                        captured.write(buffer, 0, n);
                    }
                }
                if (captured != null) types = captured.toByteArray();
                if (name.equals(OOXML_ROOT.get(ext))) rootSeen = true;
            }
            if (!rootSeen || types == null) return false;
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            var document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(types));
            var overrides = document.getElementsByTagNameNS("http://schemas.openxmlformats.org/package/2006/content-types", "Override");
            for (int i = 0; i < overrides.getLength(); i++) {
                var attrs = overrides.item(i).getAttributes();
                if (attrs.getNamedItem("PartName") != null && attrs.getNamedItem("ContentType") != null
                        && ("/" + OOXML_ROOT.get(ext)).equals(attrs.getNamedItem("PartName").getNodeValue())
                        && OOXML_TYPE.get(ext).equals(attrs.getNamedItem("ContentType").getNodeValue())) return true;
            }
        } catch (Exception exception) { return false; }
        return false;
    }

    private static boolean starts(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) if (bytes[i] != prefix[i]) return false;
        return true;
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_FILE", message);
    }
    private static BusinessException tooLarge() {
        return new BusinessException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "文件超过大小限制");
    }
}
