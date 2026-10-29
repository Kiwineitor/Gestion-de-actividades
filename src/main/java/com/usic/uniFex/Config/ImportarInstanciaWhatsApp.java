package com.usic.uniFex.Config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import com.usic.uniFex.model.service.InstanciaWhatsAppService;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ImportarInstanciaWhatsApp {
    private final InstanciaWhatsAppService instancias;
    @Value("${whatsapp.enabled:false}") private boolean habilitada;
    @Value("${whatsapp.api.url:}") private String url;
    @Value("${whatsapp.api.key:}") private String clave;
    @Value("${whatsapp.instance:}") private String instancia;

    @EventListener(ApplicationReadyEvent.class)
    public void importar() {
        if (habilitada && !url.isBlank() && !clave.isBlank() && !instancia.isBlank()) {
            instancias.importarInicial(url, instancia, clave);
        }
    }
}
