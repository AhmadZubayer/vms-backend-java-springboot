package com.example.stark_vms_server.repository;

import com.example.stark_vms_server.entity.Vehicle;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    @Query("""
            select vehicle from Vehicle vehicle
            where lower(vehicle.vehicleNumber) like lower(concat('%', :term, '%'))
               or lower(vehicle.brand) like lower(concat('%', :term, '%'))
               or lower(vehicle.company) like lower(concat('%', :term, '%'))
               or lower(vehicle.model) like lower(concat('%', :term, '%'))
               or lower(vehicle.type) like lower(concat('%', :term, '%'))
               or lower(vehicle.status) like lower(concat('%', :term, '%'))
            """)
    Page<Vehicle> search(@Param("term") String term, Pageable pageable);

    @Query("""
            select vehicle from Vehicle vehicle
            where vehicle.status = :status
              and (lower(vehicle.vehicleNumber) like lower(concat('%', :term, '%'))
                or lower(vehicle.brand) like lower(concat('%', :term, '%'))
                or lower(vehicle.company) like lower(concat('%', :term, '%'))
                or lower(vehicle.model) like lower(concat('%', :term, '%'))
                or lower(vehicle.type) like lower(concat('%', :term, '%')))
            """)
    Page<Vehicle> searchByStatus(
            @Param("term") String term, @Param("status") String status, Pageable pageable
    );

    Page<Vehicle> findByStatus(String status, Pageable pageable);

    @Query("select vehicle.status as status, count(vehicle) as total from Vehicle vehicle "
            + "group by vehicle.status")
    List<StatusTotal> countGroupedByStatus();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select vehicle from Vehicle vehicle where vehicle.id = :id")
    Optional<Vehicle> findForUpdate(@Param("id") long id);

    /** One row of the vehicle count per status. */
    interface StatusTotal {

        String getStatus();

        long getTotal();
    }
}
