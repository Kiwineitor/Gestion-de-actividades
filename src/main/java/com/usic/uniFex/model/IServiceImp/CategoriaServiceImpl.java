package com.usic.uniFex.model.IServiceImp;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.usic.uniFex.model.IService.ICategoriaService;
import com.usic.uniFex.model.dao.ICategoriaDao;
import com.usic.uniFex.model.entity.Categoria;

@Service
public class CategoriaServiceImpl implements ICategoriaService {
    
    @Autowired
    private ICategoriaDao categoriaDao;

    @Override
    public List<Categoria> findAll() {
        return categoriaDao.findAll();
    }

    @Override
    public Categoria findById(Long idEntidad) {
        return categoriaDao.findById(idEntidad).orElse(null);
    }

    @Override
    public Categoria save(Categoria entidad) {
        return categoriaDao.save(entidad);
    }

    @Override
    public void deleteById(Long idEntidad) {
        categoriaDao.deleteById(idEntidad);
    }
}
