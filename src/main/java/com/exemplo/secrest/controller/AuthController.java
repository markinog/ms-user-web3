package com.exemplo.secrest.controller;

import com.exemplo.secrest.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private UserService userService;

    @PostMapping("/request-code")
    public ResponseEntity<Void> requestCode(@RequestBody Map<String, String> body) {
        userService.requestCode(body.get("email"));
        return ResponseEntity.ok().build();
    }
}
