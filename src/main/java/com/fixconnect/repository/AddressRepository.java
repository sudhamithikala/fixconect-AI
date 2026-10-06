package com.fixconnect.repository;

import com.fixconnect.domain.Address;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AddressRepository extends JpaRepository<Address, Long> {
    List<Address> findByUser_IdOrderByPrimaryAddressDescIdAsc(Long userId);

    Optional<Address> findFirstByUser_IdAndPrimaryAddressTrue(Long userId);

    Optional<Address> findByIdAndUser_Id(Long id, Long userId);

    long countByUser_Id(Long userId);
}
