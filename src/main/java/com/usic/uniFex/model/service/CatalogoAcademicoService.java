package com.usic.uniFex.model.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.usic.uniFex.model.dao.IAreaDao;
import com.usic.uniFex.model.dao.ICarreraDao;
import com.usic.uniFex.model.dto.AreaDTO;
import com.usic.uniFex.model.dto.CarreraDTO;
import com.usic.uniFex.model.entity.Carrera;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CatalogoAcademicoService {

    private final IAreaDao areaDao;
    private final ICarreraDao carreraDao;

    @Transactional(readOnly = true)
    public List<AreaDTO> areasConCarreras() {
        Map<Long, List<CarreraDTO>> porArea = carreras().stream()
                .filter(c -> c.areaId() != null)
                .collect(Collectors.groupingBy(CarreraDTO::areaId));
        return areaDao.listarVivas().stream()
                .map(a -> AreaDTO.de(a, porArea.getOrDefault(a.getId(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CarreraDTO> carreras() {
        return CarreraDTO.de(carreraDao.listarVivasConArea());
    }

    @Transactional(readOnly = true)
    public Carrera buscarViva(Long carreraId) {
        if (carreraId == null) return null;
        return carreraDao.findById(carreraId)
                .filter(c -> !Carrera.REGISTRO_ANULADO.equals(c.getEstado()))
                .orElse(null);
    }
}
