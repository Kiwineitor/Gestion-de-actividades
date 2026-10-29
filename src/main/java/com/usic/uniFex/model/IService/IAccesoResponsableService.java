package com.usic.uniFex.model.IService;

import java.util.Map;

import org.springframework.stereotype.Service;

import com.usic.uniFex.model.entity.AccesoResponsable;

@Service
public interface IAccesoResponsableService extends IServiceGenerico<AccesoResponsable, Long> {
     Map<String,Object> estadoPorCi(String ci);
    Map<String,Object> entrarPorCi(String ci);
    Map<String,Object> salirPorCi(String ci);
}
