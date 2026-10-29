package com.usic.uniFex.model.IServiceImp;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.usic.uniFex.model.IService.IInscripcionPuestoService;
import com.usic.uniFex.model.dao.IInscripcionPuestoDao;
import com.usic.uniFex.model.entity.InscripcionPuesto;

@Service
public class InscripcionPuestoServiceImpl implements IInscripcionPuestoService {
    
    @Autowired
    private IInscripcionPuestoDao inscripcionPuestoDao;

    @Override
    public List<InscripcionPuesto> findAll() {
        return inscripcionPuestoDao.findAll();
    }

    @Override
    public InscripcionPuesto findById(Long idEntidad) {
        return inscripcionPuestoDao.findById(idEntidad).orElse(null);
    }

    @Override
    public InscripcionPuesto save(InscripcionPuesto entidad) {
        return inscripcionPuestoDao.save(entidad);
    }

    @Override
    public void deleteById(Long idEntidad) {
        inscripcionPuestoDao.deleteById(idEntidad);
    }
}
