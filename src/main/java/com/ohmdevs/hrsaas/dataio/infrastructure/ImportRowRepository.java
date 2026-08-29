package com.ohmdevs.hrsaas.dataio.infrastructure;

import com.ohmdevs.hrsaas.dataio.domain.ImportRow;
import com.ohmdevs.hrsaas.dataio.domain.ImportRowStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ImportRowRepository extends JpaRepository<ImportRow, Long> {

    List<ImportRow> findByImportBatchIdOrderByLineNumberAsc(Long importBatchId);

    List<ImportRow> findByImportBatchIdAndStatusOrderByLineNumberAsc(Long importBatchId, ImportRowStatus status);
}
