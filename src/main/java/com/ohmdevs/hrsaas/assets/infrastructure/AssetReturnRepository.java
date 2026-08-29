package com.ohmdevs.hrsaas.assets.infrastructure;

import com.ohmdevs.hrsaas.assets.domain.AssetReturn;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetReturnRepository extends JpaRepository<AssetReturn, Long> {
}
