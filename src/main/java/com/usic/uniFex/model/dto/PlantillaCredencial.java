package com.usic.uniFex.model.dto;

import java.util.List;
import java.util.Optional;

public record PlantillaCredencial(
        String id,
        String etiqueta,
        String detalle,
        String imagen,
        boolean requiereFoto,
        boolean centrado,
        boolean mayusculas,
        Caja qr,
        Caja nombre,
        Caja empresa,
        Caja ci,
        Caja codigo,
        Caja zona,
        Caja foto,
        Caja tipo,
        String tipoTexto,
        String colorTexto,
        boolean fotoCircular,
        boolean multilinea,
        String imagenReverso) {

    public record Caja(double x, double y, double ancho, double alto) {
    }

    public PlantillaCredencial(String id, String etiqueta, String detalle, String imagen,
                               boolean requiereFoto, boolean centrado, boolean mayusculas,
                               Caja qr, Caja nombre, Caja empresa, Caja ci, Caja codigo,
                               Caja zona, Caja foto) {
        this(id, etiqueta, detalle, imagen, requiereFoto, centrado, mayusculas,
                qr, nombre, empresa, ci, codigo, zona, foto, null, null, null,
                "EXPOSITOR".equals(id) || "CREDENCIAL_VIRTUAL".equals(id),
                "EXPOSITOR".equals(id), null);
    }

    public static final PlantillaCredencial CON_ETIQUETAS = new PlantillaCredencial(
            "CON_ETIQUETAS", "Con etiquetas", "QR arriba a la derecha",
            "static/assets/CREDENCIAL_4.jpg",
            true,
            false,
            true,
            new Caja(0.703, 0.050, 0.225, 0.225),
            new Caja(0.165, 0.502, 0.758, 0.048),
            new Caja(0.165, 0.608, 0.758, 0.048),
            new Caja(0.165, 0.713, 0.431, 0.048),
            new Caja(0.658, 0.713, 0.182, 0.048),
            null,
            null);

    public static final PlantillaCredencial QR_GRANDE = new PlantillaCredencial(
            "QR_GRANDE", "QR grande", "QR centrado, sin etiquetas",
            "static/assets/CREDENCIAL3.jpg",
            false,
            true,
            true,
            new Caja(0.3125, 0.170, 0.375, 0.375),
            null,
            null,
            null,
            new Caja(0.147, 0.474, 0.794, 0.076),
            null,
            null);

    public static final PlantillaCredencial EXPOSITOR = new PlantillaCredencial(
            "EXPOSITOR", "Expositor", "Plantilla para expositores (foto izq, QR der, datos abajo)",
            "static/assets/CREDENCIAL_EXPOSITOR_NUEVO.png",
            true,
            false,
            true,
            new Caja(606.0 / 1102, 121.0 / 1427, 309.0 / 1102, 309.0 / 1427),
            new Caja(136.0 / 1102, 657.0 / 1427, 829.0 / 1102, 48.0 / 1427),
            new Caja(136.0 / 1102, 794.0 / 1427, 832.0 / 1102, 57.0 / 1427),
            new Caja(135.0 / 1102, 936.0 / 1427, 832.0 / 1102, 49.0 / 1427),
            new Caja(135.0 / 1102, 1071.0 / 1427, 394.0 / 1102, 57.0 / 1427),
            new Caja(571.0 / 1102, 1070.0 / 1427, 396.0 / 1102, 57.0 / 1427),
            new Caja(146.0 / 1102, 257.0 / 1427, 320.0 / 1102, 318.0 / 1427)
    );

    public static final PlantillaCredencial CREDENCIAL_VIRTUAL = new PlantillaCredencial(
            "CREDENCIAL_VIRTUAL", "Credencial virtual", "Para el teléfono, con foto y QR",
            "static/assets/credencialvirtual.jpeg",
            true,
            false,
            true,
            new Caja(0.503, 0.029, 0.440, 0.440),
            new Caja(0.1422, 0.5119, 0.808, 0.053),
            new Caja(0.1422, 0.6006, 0.809, 0.054),
            new Caja(0.1422, 0.6900, 0.808, 0.054),
            new Caja(0.1433, 0.7794, 0.311, 0.054),
            new Caja(0.4800, 0.7794, 0.471, 0.054),
            new Caja(0.1450, 0.2472, 0.4000, 0.2250));

    public static final List<PlantillaCredencial> CATALOGO =
            List.of(CREDENCIAL_VIRTUAL, CON_ETIQUETAS, QR_GRANDE, EXPOSITOR);

    public static final PlantillaCredencial POR_DEFECTO = CREDENCIAL_VIRTUAL;

    private static volatile List<PlantillaCredencial> vigentes = CATALOGO;
    private static volatile String idPorDefecto = POR_DEFECTO.id();

    public static void registrar(List<PlantillaCredencial> plantillas, String porDefecto) {
        if (plantillas == null || plantillas.isEmpty()) {
            vigentes = CATALOGO;
            idPorDefecto = POR_DEFECTO.id();
            return;
        }
        vigentes = List.copyOf(plantillas);
        idPorDefecto = plantillas.stream().anyMatch(p -> p.id().equals(porDefecto))
                ? porDefecto : plantillas.get(0).id();
    }

    public static List<PlantillaCredencial> catalogo() {
        return vigentes;
    }

    public static PlantillaCredencial porDefecto() {
        return vigentes.stream().filter(p -> p.id().equals(idPorDefecto)).findFirst().orElse(POR_DEFECTO);
    }

    public static PlantillaCredencial actual(PlantillaCredencial base) {
        return vigentes.stream().filter(p -> p.id().equals(base.id())).findFirst().orElse(base);
    }

    public static Optional<PlantillaCredencial> de(String id) {
        String n = id == null ? "" : id.trim().toUpperCase();
        return vigentes.stream().filter(p -> p.id().equals(n)).findFirst();
    }

    public static PlantillaCredencial oPorDefecto(String id) {
        return de(id).orElseGet(PlantillaCredencial::porDefecto);
    }
}
