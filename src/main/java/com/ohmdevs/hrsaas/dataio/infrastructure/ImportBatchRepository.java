package com.ohmdevs.hrsaas.dataio.infrastructure;

import com.ohmdevs.hrsaas.dataio.domain.ImportBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ImportBatchRepository extends JpaRepository<ImportBatch, Long> {

    /** Idempotencia: mismo archivo (mismo hash) no crea un segundo lote. */
    Optional<ImportBatch> findByFileHash(String fileHash);
}
