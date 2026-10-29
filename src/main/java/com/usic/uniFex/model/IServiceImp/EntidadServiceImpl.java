package com.usic.uniFex.model.IServiceImp;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.usic.uniFex.model.IService.IEntidadService;
import com.usic.uniFex.model.dao.IEntidadDao;
import com.usic.uniFex.model.entity.Entidad;

@Service
public class EntidadServiceImpl implements IEntidadService{
    
    @Autowired
    private IEntidadDao entidadDao;

    @Override
    public List<Entidad> findAll() {
        return entidadDao.findAll();
    }

    @Override
    public Entidad findById(Long idEntidad) {
        return entidadDao.findById(idEntidad).orElse(null);
    }

    @Override
    public Entidad save(Entidad entidad) {
        return entidadDao.save(entidad);
    }

    @Override
    public void deleteById(Long idEntidad) {
        entidadDao.deleteById(idEntidad);
    }
}
