package com.usic.uniFex.controller.publico;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class CredencialQrController {

    @GetMapping("/credencial/**")
    public String credencial() {
        return "forward:/app/index.html";
    }
}
