package org.comment.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
            title = "hm-comment",
            description = "黑马点评系统后端Java项目",
            version = "1.0"
        )
)
public class SpringDocConfig {
}
