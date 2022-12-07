package org.comment.controller;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.comment.dto.Result;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;

import static org.comment.utils.SystemConstants.IMAGE_UPLOAD_DIR;

@Slf4j
@RestController
@RequestMapping("/upload")
public class UploadController {

    @PostMapping("/blog")
    public Result uploadImage(@RequestParam("file") MultipartFile image) {
        String fileName = null;
        try {
            // 1.获取原始文件名称
            String originalFilename = image.getOriginalFilename();

            // 2.生成新文件名
            fileName = createNewFileName(originalFilename);
            log.info("fileName = {}", fileName);

            // 3.保存文件
            image.transferTo(new File(IMAGE_UPLOAD_DIR, fileName));
            log.debug("文件上传成功，{}", fileName);
        } catch (IOException e) {
            log.error("保存文件失败！", e);
        }
        return Result.ok(fileName);
    }

    private static String createNewFileName(String originalFilename) {
        // 1.获取文件后缀
        String suffix = StrUtil.subAfter(originalFilename, ".", true);

        // 2.生成目录
        String name = UUID.randomUUID().toString();
        int hash = name.hashCode();
        int d1 = hash & 0xF;
        int d2 = (hash >> 4) & 0xF;

        // 3.判断目录是否存在
        File dir = new File(IMAGE_UPLOAD_DIR, StrUtil.format("/blogs/{}/{}", d1, d2));
        if (!dir.exists()) {
            boolean result = dir.mkdirs();
            if (!result)
                log.error("生成目录失败！");
        }

        // 4.生成文件名
        return StrUtil.format("/blogs/{}/{}/{}.{}", d1, d2, name, suffix);
    }
}
