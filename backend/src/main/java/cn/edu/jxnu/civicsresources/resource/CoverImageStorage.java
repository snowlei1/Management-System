package cn.edu.jxnu.civicsresources.resource;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import static cn.edu.jxnu.civicsresources.resource.FavoriteFolderService.failure;

/** Re-encodes bounded JPEG/PNG input; SVG, URLs and arbitrary filesystem paths are never accepted. */
@Component
public class CoverImageStorage {
 private final Path root;
 private static final int LIMIT=2*1024*1024;
 public record Saved(String key,int size){}
 public CoverImageStorage(ResourceStorageProperties properties){root=Path.of(properties.directory()).toAbsolutePath().normalize().resolve("covers");}
 public Saved save(MultipartFile file){
  if(file==null||file.isEmpty()||file.getSize()>LIMIT)throw failure(400,"封面须为不超过2MiB的非空PNG/JPEG图片");
  String name=LocalResourceFileStorage.safeOriginalName(file.getOriginalFilename()).toLowerCase(Locale.ROOT);
  boolean png=name.endsWith(".png"),jpeg=name.endsWith(".jpg")||name.endsWith(".jpeg");
  if(!(png&&"image/png".equals(file.getContentType())||jpeg&&"image/jpeg".equals(file.getContentType())))throw failure(400,"仅接受扩展名和类型匹配的PNG/JPEG封面");
  try(var in=file.getInputStream()){
   byte[] source=in.readNBytes(LIMIT+1);if(source.length>LIMIT)throw failure(400,"封面超过2MiB");
   if(png&&(source.length<8||source[0]!=(byte)137||source[1]!=80)||jpeg&&(source.length<3||source[0]!=(byte)255||source[1]!=(byte)216))throw failure(400,"图片内容与类型不匹配");
   try(var imageInput=ImageIO.createImageInputStream(new ByteArrayInputStream(source))){
    var readers=ImageIO.getImageReaders(imageInput);if(!readers.hasNext())throw failure(400,"封面图片无法解析");var reader=readers.next();
    try{reader.setInput(imageInput);int w=reader.getWidth(0),h=reader.getHeight(0);if(w<1||h<1||w>4096||h>4096||(long)w*h>16000000)throw failure(400,"封面尺寸不得超过4096边长及1600万像素");
     var image=reader.read(0);var output=new ByteArrayOutputStream();ImageIO.write(image,"png",output);byte[] clean=output.toByteArray();if(clean.length>LIMIT)throw failure(400,"封面标准化后超过2MiB，请压缩图片");
     Files.createDirectories(root);if(Files.isSymbolicLink(root)||Files.isSymbolicLink(root.getParent()))throw new IOException();String key=UUID.randomUUID()+".png";Files.write(root.resolve(key),clean,StandardOpenOption.CREATE_NEW);return new Saved(key,clean.length);
    }finally{reader.dispose();}
   }
  }catch(IOException e){throw failure(400,"封面图片无法读取或保存");}
 }
 public byte[] read(String key,int size){
  if(key==null||!key.matches("[a-f0-9-]{36}\\.png")||size<1||size>LIMIT)throw failure(404,"未设置可用封面");
  Path path=root.resolve(key);try{if(Files.isSymbolicLink(root)||Files.isSymbolicLink(root.getParent())||!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||!path.toRealPath().startsWith(root.toRealPath())||Files.size(path)!=size)throw failure(404,"封面暂不可用");return Files.readAllBytes(path);}catch(IOException e){throw failure(404,"封面暂不可用");}
 }
 public void discard(String key){if(key!=null&&key.matches("[a-f0-9-]{36}\\.png"))try{Files.deleteIfExists(root.resolve(key));}catch(IOException e){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Cover cleanup requires reconciliation: {}",key);}}
}
