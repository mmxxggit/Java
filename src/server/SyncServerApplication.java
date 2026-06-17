package server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 数据同步 HTTP 服务端入口
 */
@SpringBootApplication
public class SyncServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(SyncServerApplication.class, args);
    }
}
