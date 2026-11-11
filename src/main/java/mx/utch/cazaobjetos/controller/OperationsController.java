package mx.utch.cazaobjetos.controller;
import java.util.Map;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessException;
@RestController
@RequestMapping("${url.api}")
public class OperationsController {
    private final JdbcTemplate db;
    public OperationsController(JdbcTemplate db) { this.db=db; }
    @GetMapping(value="/openapi.json",produces=MediaType.APPLICATION_JSON_VALUE)
    public Resource contract() { return new ClassPathResource("openapi.json"); }
    @GetMapping("/health")
    public ResponseEntity<Map<String,String>> health() {
        try { db.queryForObject("select 1",Integer.class);return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("status","UP")); }
        catch(DataAccessException e) { return ResponseEntity.status(503).cacheControl(CacheControl.noStore()).body(Map.of("status","DOWN")); }
    }
}
