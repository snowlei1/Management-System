package cn.edu.jxnu.civicsresources.resource;
import static org.junit.jupiter.api.Assertions.*;
import cn.edu.jxnu.civicsresources.common.BusinessException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;
class CoverImageStorageTest {
 @TempDir Path dir;
 CoverImageStorage storage(){return new CoverImageStorage(new ResourceStorageProperties(dir.toString(),DataSize.ofMegabytes(20),List.of("png","jpg")));}
 @Test void reencodedPngCanBeRead()throws Exception{var out=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(30,20,BufferedImage.TYPE_INT_RGB),"png",out);var s=storage();var saved=s.save(new MockMultipartFile("file","cover.png","image/png",out.toByteArray()));assertTrue(s.read(saved.key(),saved.size()).length>0);s.discard(saved.key());assertThrows(BusinessException.class,()->s.read(saved.key(),saved.size()));}
 @Test void rejectSvgAndFakePng(){var s=storage();assertThrows(BusinessException.class,()->s.save(new MockMultipartFile("file","x.svg","image/svg+xml","<svg/>".getBytes())));assertThrows(BusinessException.class,()->s.save(new MockMultipartFile("file","x.png","image/png","not an image".getBytes())));}
 @Test void rejectLargeUploadAndTraversal(){var s=storage();assertThrows(BusinessException.class,()->s.save(new MockMultipartFile("file","x.png","image/png",new byte[2*1024*1024+1])));assertThrows(BusinessException.class,()->s.read("../cover.png",1));}
 @Test void optionalTextAndFolderValidation(){assertNull(ResourceSupplementService.text("   ",120));assertEquals("作者",ResourceSupplementService.text(" 作者 ",120));assertThrows(BusinessException.class,()->ResourceSupplementService.text("x".repeat(121),120));assertThrows(BusinessException.class,()->FavoriteFolderService.normalizeName(" "));assertEquals("课程资料",FavoriteFolderService.normalizeName(" 课程资料 "));}
}
