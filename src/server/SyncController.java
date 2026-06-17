package server;

import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 账本同步接口
 */
@RestController
@RequestMapping("/api/sync")
public class SyncController {

    private final SyncStorageService storageService;

    public SyncController(SyncStorageService storageService) {
        this.storageService = storageService;
    }

    /**
     * 上传本地账本到服务端。
     *
     * 示例：POST /api/sync/upload?username=alice
     */
    @PostMapping(value = "/upload", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> upload(@RequestParam String username, @RequestBody String body) {
        return storageService.upload(username, body);
    }

    /**
     * 从服务端拉取指定用户账本。
     *
     * 示例：GET /api/sync/pull/alice
     */
    @GetMapping(value = "/pull/{username}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> pull(@PathVariable String username) {
        String data = storageService.pull(username);
        if (data == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(data);
    }

    /**
     * 查询服务端整体状态。
     */
    @GetMapping("/status")
    public Map<String, Object> status() {
        return storageService.status();
    }

    /**
     * 查询指定用户在服务端的同步状态。
     */
    @GetMapping("/status/{username}")
    public Map<String, Object> userStatus(@PathVariable String username) {
        return storageService.userStatus(username);
    }
}
