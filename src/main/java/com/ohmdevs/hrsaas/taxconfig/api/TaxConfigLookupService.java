package com.ohmdevs.hrsaas.taxconfig.api;

import com.ohmdevs.hrsaas.taxconfig.domain.ContributionScheme;
import com.ohmdevs.hrsaas.taxconfig.domain.SeveranceScale;
import com.ohmdevs.hrsaas.taxconfig.domain.TaxBracket;
import com.ohmdevs.hrsaas.taxconfig.infrastructure.ContributionSchemeRepository;
import com.ohmdevs.hrsaas.taxconfig.infrastructure.SeveranceScaleRepository;
import com.ohmdevs.hrsaas.taxconfig.infrastructure.StatutoryParameterRepository;
import com.ohmdevs.hrsaas.taxconfig.infrastructure.TaxBracketRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Único contenido público de {@code taxconfig}: "¿qué aplicaba el
 * {@code date}?", no "¿cuál es la escala?" (AD-04). Cada método resuelve
 * contra la fila cuya vigencia cubre la fecha pedida — nunca la fila "más
 * reciente" sin más, que daría la respuesta equivocada al recalcular un
 * período pasado tras un cambio de tasa.
 */
@Service
public class TaxConfigLookupService {

    private final ContributionSchemeRepository contributionSchemeRepository;
    private final TaxBracketRepository taxBracketRepository;
    private final StatutoryParameterRepository statutoryParameterRepository;
    private final SeveranceScaleRepository severanceScaleRepository;

    public TaxConfigLookupService(ContributionSchemeRepository contributionSchemeRepository,
                                   TaxBracketRepository taxBracketRepository,
                                   StatutoryParameterRepository statutoryParameterRepository,
                                   SeveranceScaleRepository severanceScaleRepository) {
        this.contributionSchemeRepository = contributionSchemeRepository;
        this.taxBracketRepository = taxBracketRepository;
        this.statutoryParameterRepository = statutoryParameterRepository;
        this.severanceScaleRepository = severanceScaleRepository;
    }

    @Transactional
    public Optional<ContributionScheme> schemeAsOf(String code, LocalDate date) {
        return contributionSchemeRepository.findActiveOn(code, date);
    }

    @Transactional
    public List<TaxBracket> bracketsAsOf(String schemeCode, LocalDate date) {
        return taxBracketRepository.findActiveOn(schemeCode, date);
    }

    @Transactional
    public Optional<BigDecimal> parameterAsOf(String code, LocalDate date) {
        return statutoryParameterRepository.findActiveOn(code, date).map(p -> p.getValue());
    }

    @Transactional
    public List<SeveranceScale> severanceScaleAsOf(LocalDate date) {
        return severanceScaleRepository.findActiveOn(date);
    }
}
