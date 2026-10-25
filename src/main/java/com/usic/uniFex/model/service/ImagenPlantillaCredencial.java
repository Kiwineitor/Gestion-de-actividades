package com.usic.uniFex.model.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class ImagenPlantillaCredencial {

    public static final String PREFIJO_SUBIDA = "/files/";

    @Value("${app.upload-root:uploads}")
    private String uploadRoot;

    private final Map<String, byte[]> cache = new ConcurrentHashMap<>();

    public byte[] bytes(String imagen) {
        if (imagen == null || imagen.isBlank()) {
            throw new IllegalStateException("La plantilla no tiene imagen de fondo");
        }
        return cache.computeIfAbsent(imagen, this::leer);
    }

    public boolean existe(String imagen) {
        try {
            bytes(imagen);
            return true;
        } catch (IllegalStateException e) {
            return false;
        }
    }

    private byte[] leer(String imagen) {
        try {
            if (imagen.startsWith(PREFIJO_SUBIDA)) {
                Path base = Paths.get(uploadRoot).toAbsolutePath().normalize();
                Path destino = base.resolve(imagen.substring(PREFIJO_SUBIDA.length())).normalize();
                if (!destino.startsWith(base) || !Files.isRegularFile(destino)) {
                    throw new IOException("archivo inexistente");
                }
                return Files.readAllBytes(destino);
            }
            try (InputStream in = new ClassPathResource(imagen).getInputStream()) {
                return in.readAllBytes();
            }
        } catch (IOException e) {
            throw new IllegalStateException("No se encontro la imagen de la plantilla: " + imagen, e);
        }
    }
}
