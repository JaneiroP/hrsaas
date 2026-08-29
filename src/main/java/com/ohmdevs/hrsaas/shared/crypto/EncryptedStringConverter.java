package com.ohmdevs.hrsaas.shared.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;

/**
 * Adaptador de {@link AesGcmEncryptionService} a JPA.
 *
 * <p>{@code autoApply = false} (el valor por defecto de {@link Converter}) es
 * deliberado y no se cambia: cada entidad aplica este conversor explícitamente
 * con {@code @Convert(converter = EncryptedStringConverter.class)} en el campo
 * exacto que debe cifrarse. Un {@code autoApply = true} cifraría por sorpresa
 * cualquier {@code String} nuevo que alguien añada a una entidad — exactamente
 * el tipo de "magia a distancia" que este proyecto evita.
 *
 * <p>Es {@code @Component} en vez de estático porque necesita
 * {@link AesGcmEncryptionService} inyectado (que a su vez necesita la clave de
 * configuración); Hibernate resuelve conversores como beans de Spring desde
 * Boot 2.x en adelante, no hace falta configuración extra.
 */
@Converter
@Component
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private final AesGcmEncryptionService encryptionService;

    public EncryptedStringConverter(AesGcmEncryptionService encryptionService) {
        this.encryptionService = encryptionService;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return encryptionService.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return encryptionService.decrypt(dbData);
    }
}
