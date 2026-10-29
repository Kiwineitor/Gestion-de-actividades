package com.usic.uniFex.model.IServiceImp;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.usic.uniFex.model.IService.IPuestoService;
import com.usic.uniFex.model.dao.IPuestoDao;
import com.usic.uniFex.model.entity.Puesto;

@Service
public class PuestoServiceImpl implements IPuestoService {
    
    @Autowired
    private IPuestoDao puestoDao;

    @Override
    public List<Puesto> findAll() {
        return puestoDao.findAll();
    }

    @Override
    public Puesto findById(Long idEntidad) {
        return puestoDao.findById(idEntidad).orElse(null);
    }

    @Override
    public Puesto save(Puesto entidad) {
        return puestoDao.save(entidad);
    }

    @Override
    public void deleteById(Long idEntidad) {
        puestoDao.deleteById(idEntidad);
    }

    @Override
    public List<Puesto> listarConCategoria() {
        return puestoDao.findAll();
    }
    
    @Override
    public List<Puesto> listarPuestos() {
       return puestoDao.listarPuestos();
    }

    @Override
    public List<Puesto> listarLibresPorCategoria(Long categoriaId) {
        return puestoDao.findLibresPorCategoriaOrdenados("L", categoriaId);
    }

}
