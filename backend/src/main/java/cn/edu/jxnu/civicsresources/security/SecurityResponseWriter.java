package cn.edu.jxnu.civicsresources.security;

import cn.edu.jxnu.civicsresources.common.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;

public class SecurityResponseWriter {
    private final ObjectMapper mapper;

    public SecurityResponseWriter(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public void write(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(response.getWriter(), ApiResponse.error(code, message));
    }
}
