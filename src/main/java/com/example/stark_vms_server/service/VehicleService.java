package com.example.stark_vms_server.service;

import static com.example.stark_vms_server.exception.GlobalExceptionHandler.pageOffsetTooLarge;
import static com.example.stark_vms_server.exception.GlobalExceptionHandler.unsortableProperty;
import static com.example.stark_vms_server.exception.GlobalExceptionHandler.vehicleNotFound;

import com.example.stark_vms_server.dto.VehicleResponse;
import com.example.stark_vms_server.dto.VehicleStatsResponse;
import com.example.stark_vms_server.dto.VehicleWriteRequest;
import com.example.stark_vms_server.entity.Vehicle;
import com.example.stark_vms_server.repository.VehicleRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VehicleService {
    private static final Set<String> SORTABLE_PROPERTIES = Set.of(
            "id", "vehicleNumber", "brand", "company", "model", "type", "status"
    );

    private final VehicleRepository vehicleRepository;

    public VehicleService(VehicleRepository vehicleRepository) {
        this.vehicleRepository = vehicleRepository;
    }

    @Transactional
    public VehicleResponse create(VehicleWriteRequest request) {
        Vehicle vehicle = new Vehicle(
                request.vehicleNumber().strip(),
                request.brand(),
                request.company(),
                request.model(),
                request.type().name(),
                request.status().name()
        );

        return VehicleResponse.from(vehicleRepository.saveAndFlush(vehicle));
    }

    @Transactional(readOnly = true)
    public VehicleResponse findById(long id) {
        return vehicleRepository.findById(id)
                .map(VehicleResponse::from)
                .orElseThrow(() -> vehicleNotFound(id));
    }

    @Transactional(readOnly = true)
    public Page<VehicleResponse> search(String query, Vehicle.Status status, Pageable pageable) {
        checkPageable(pageable);

        String term = query == null ? "" : query.strip();
        Page<Vehicle> vehicles = findPage(term, status, pageable);

        return vehicles.map(VehicleResponse::from);
    }

    @Transactional(readOnly = true)
    public VehicleStatsResponse stats() {
        Map<String, Long> totals = new LinkedHashMap<>();
        for (Vehicle.Status status : Vehicle.Status.values()) {
            totals.put(status.name(), 0L);
        }

        long total = 0;
        for (VehicleRepository.StatusTotal row : vehicleRepository.countGroupedByStatus()) {
            totals.merge(row.getStatus(), row.getTotal(), Long::sum);
            total += row.getTotal();
        }

        return new VehicleStatsResponse(total, Map.copyOf(totals));
    }

    private Page<Vehicle> findPage(String term, Vehicle.Status status, Pageable pageable) {
        if (status == null) {
            return term.isEmpty()
                    ? vehicleRepository.findAll(pageable)
                    : vehicleRepository.search(term, pageable);
        }

        return term.isEmpty()
                ? vehicleRepository.findByStatus(status.name(), pageable)
                : vehicleRepository.searchByStatus(term, status.name(), pageable);
    }

    @Transactional
    public VehicleResponse update(long id, VehicleWriteRequest request) {
        Vehicle vehicle = vehicleRepository.findForUpdate(id)
                .orElseThrow(() -> vehicleNotFound(id));

        vehicle.updateDetails(
                request.vehicleNumber().strip(),
                request.brand(),
                request.company(),
                request.model(),
                request.type().name(),
                request.status().name()
        );

        return VehicleResponse.from(vehicleRepository.saveAndFlush(vehicle));
    }

    @Transactional
    public void delete(long id) {
        Vehicle vehicle = vehicleRepository.findForUpdate(id)
                .orElseThrow(() -> vehicleNotFound(id));

        vehicleRepository.delete(vehicle);
    }

    private static void checkPageable(Pageable pageable) {
        if (pageable.getOffset() > Integer.MAX_VALUE) {
            throw pageOffsetTooLarge();
        }

        for (Sort.Order order : pageable.getSort()) {
            if (!SORTABLE_PROPERTIES.contains(order.getProperty())) {
                throw unsortableProperty(order.getProperty());
            }
        }
    }
}
