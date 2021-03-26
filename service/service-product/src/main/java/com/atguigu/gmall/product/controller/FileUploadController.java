package com.atguigu.gmall.product.controller;

import com.atguigu.gmall.common.result.Result;
import io.swagger.annotations.Api;
import org.apache.commons.io.FilenameUtils;
import org.csource.common.MyException;
import org.csource.fastdfs.ClientGlobal;
import org.csource.fastdfs.StorageClient1;
import org.csource.fastdfs.TrackerClient;
import org.csource.fastdfs.TrackerServer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Api(tags = "文件上传接口")
@RestController
@RequestMapping("admin/product/")
public class FileUploadController {

    @Value("${fileServer.url}")
    private String fileUrl;

    @PostMapping("fileUpload")
    public Result fileUpload(MultipartFile file) throws IOException, MyException {
         /*
            1.  读取到tracker.conf 文件
            2.  初始化FastDFS
            3.  创建对应的TrackerClient,TrackerServer
            4.  创建一个StorageClient，调用文件上传方法
            5.  获取到文件上传的url 并返回
         */
        String configFile = this.getClass().getResource("/tracker.conf").getFile(); //1
        //  表示记录文件url
        String path = null;

        if(configFile != null){
            //读取到了数据
            ClientGlobal.init(configFile); //2
            TrackerClient trackerClient = new TrackerClient(); //3
            TrackerServer trackerServer = trackerClient.getConnection(); //3
            StorageClient1 storageClient1 = new StorageClient1(trackerServer,null); //4
            //获取文件后缀名
            String extName = FilenameUtils.getExtension(file.getOriginalFilename());
            path = storageClient1.upload_appender_file1(file.getBytes(),extName,null); //5
            //文件上传之后的全路径
            System.out.println("文件上传之后的全路径:\t"+fileUrl + path);
        }
        //  将文件的整体全路径放入data 中
        return Result.ok(fileUrl + path);

    }
}
