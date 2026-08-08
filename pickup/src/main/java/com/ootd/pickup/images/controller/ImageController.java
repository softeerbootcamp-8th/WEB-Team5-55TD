package com.ootd.pickup.images.controller;

import com.ootd.pickup.global.auth.annotation.MemberId;
import com.ootd.pickup.global.auth.annotation.RequireAuthentication;
import com.ootd.pickup.images.dto.CreateImageUploadRequest;
import com.ootd.pickup.images.dto.CreateImageUploadResponse;
import com.ootd.pickup.images.service.ImageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/image-uploads")
public class ImageController {

  private final ImageService imageService;

  @PostMapping
  @RequireAuthentication
  public ResponseEntity<CreateImageUploadResponse> createUpload(
      @MemberId Long memberId, @Valid @RequestBody CreateImageUploadRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(imageService.createUpload(memberId, request));
  }
}
