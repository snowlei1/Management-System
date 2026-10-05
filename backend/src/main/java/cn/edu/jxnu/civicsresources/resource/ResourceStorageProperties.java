package cn.edu.jxnu.civicsresources.resource;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

@Configuration
@EnableConfigurationProperties(ResourceStorageProperties.class)
class ResourceStorageConfiguration { }

@ConfigurationProperties(prefix = "app.resource-storage")
public record ResourceStorageProperties(String directory, DataSize maxSize, List<String> allowedExtensions) { }
