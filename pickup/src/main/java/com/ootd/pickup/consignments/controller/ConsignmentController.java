package com.ootd.pickup.consignments.controller;

import com.ootd.pickup.consignments.dto.request.RegisterConsignmentRequest;
import com.ootd.pickup.consignments.dto.response.RegisterConsignmentResponse;
import com.ootd.pickup.consignments.service.ConsignmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/consignments")
@RequiredArgsConstructor
public class ConsignmentController {

    private final ConsignmentService consignmentService;

    @PostMapping
    public ResponseEntity<RegisterConsignmentResponse> registerConsignment(@Valid @RequestBody RegisterConsignmentRequest registerConsignmentRequest, Long sellerMemberId){
        return ResponseEntity.status(HttpStatus.CREATED).body(consignmentService.registerConsignment(sellerMemberId, registerConsignmentRequest));
    }
}
