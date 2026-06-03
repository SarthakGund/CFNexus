package com.cfduel.health;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/checkpoint")
public class CheckpointController {

    @GetMapping("/heat")
    public ResponseEntity<String> heat() {
        return ResponseEntity.ok("ok");
    }
}
