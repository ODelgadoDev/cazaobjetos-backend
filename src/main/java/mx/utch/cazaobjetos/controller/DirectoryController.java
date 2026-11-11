package mx.utch.cazaobjetos.controller;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import mx.utch.cazaobjetos.dto.*;
import mx.utch.cazaobjetos.service.DirectoryService;
@RestController
@RequestMapping("${url.api}/responsibles")
public class DirectoryController {
    private final DirectoryService service;
    public DirectoryController(DirectoryService service) { this.service=service; }
    @GetMapping
    public PageResponseDto<DirectoryUserDto> list(@Valid @ModelAttribute DirectoryQueryDto query) { return service.list(query); }
}
