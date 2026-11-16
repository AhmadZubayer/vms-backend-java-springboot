package com.example.stark_vms_server.controller;

import com.example.stark_vms_server.dto.SendResponse;
import com.example.stark_vms_server.dto.VehicleResponse;
import com.example.stark_vms_server.dto.VehicleStatsResponse;
import com.example.stark_vms_server.dto.VehicleWriteRequest;
import com.example.stark_vms_server.entity.Vehicle;
import com.example.stark_vms_server.service.VehicleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.Arrays;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/vehicles")
public class VehicleController {

    private final VehicleService vehicleService;

    public VehicleController(VehicleService vehicleService) {
        this.vehicleService = vehicleService;
    }

    @PostMapping
    public ResponseEntity<SendResponse<VehicleResponse>> create(
            @Valid @RequestBody VehicleWriteRequest request) {
        VehicleResponse vehicle = vehicleService.create(request);

        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/{id}")
                .buildAndExpand(vehicle.id())
                .toUri();

        return ResponseEntity.created(location).body(SendResponse.of(vehicle));
    }

    @GetMapping("/stats")
    public ResponseEntity<SendResponse<VehicleStatsResponse>> stats() {
        return ResponseEntity.ok(SendResponse.of(vehicleService.stats()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SendResponse<VehicleResponse>> findById(@PathVariable("id") long id) {
        return ResponseEntity.ok(SendResponse.of(vehicleService.findById(id)));
    }

    @GetMapping
    public ResponseEntity<SendResponse<PagedModel<VehicleResponse>>> search(
            @RequestParam(name = "q", required = false) @Size(max = 64) String q,
            @RequestParam(name = "status", required = false) String status,
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(SendResponse.of(
                new PagedModel<>(vehicleService.search(q, parseStatus(status), pageable))
        ));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SendResponse<VehicleResponse>> update(
            @PathVariable("id") long id,
            @Valid @RequestBody VehicleWriteRequest request) {
        return ResponseEntity.ok(SendResponse.of(vehicleService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") long id) {
        vehicleService.delete(id);

        return ResponseEntity.noContent().build();
    }

    /** Reads the optional status filter, ignoring case and surrounding spaces. */
    private static Vehicle.Status parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }

        try {
            return Vehicle.Status.from(status);
        } catch (IllegalArgumentException unknown) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown status '"
                    + status + "'. Use one of " + Arrays.toString(Vehicle.Status.values()) + ".");
        }
    }
}
