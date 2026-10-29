package com.usic.uniFex.Config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

@Configuration
public class CarpetasDeSubidaConfig {

    private static final Logger logger = LoggerFactory.getLogger(CarpetasDeSubidaConfig.class);

    @Value("${app.upload-root:uploads}")
    private String raizDeSubidas;

    @Value("${spring.servlet.multipart.location:}")
    private String carpetaTemporal;

    @PostConstruct
    public void prepararCarpetasDeSubida() {
        asegurar(raizDeSubidas, "raiz de subidas (app.upload-root)");
        asegurar(carpetaTemporal, "temporal de multipart (spring.servlet.multipart.location)");
    }

    private void asegurar(String ruta, String para) {
        if (ruta == null || ruta.isBlank()) return;
        try {
            Path p = Paths.get(ruta);
            if (Files.isDirectory(p)) return;
            Files.createDirectories(p);
            logger.info("Carpeta creada, {}: {}", para, p.toAbsolutePath());
        } catch (IOException | RuntimeException e) {
            logger.warn("No se pudo crear la carpeta {} en '{}': {}. Las subidas de archivos "
                    + "fallaran hasta que exista y sea escribible.", para, ruta, e.getMessage());
        }
    }
}
