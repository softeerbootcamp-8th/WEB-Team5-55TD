package com.ootd.pickup.images.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ootd.pickup.global.auth.Authentication;
import com.ootd.pickup.global.auth.AuthenticationAttributes;
import com.ootd.pickup.global.slack.SlackErrorNotifier;
import com.ootd.pickup.images.domain.ImagePurpose;
import com.ootd.pickup.images.dto.CreateImageUploadRequest;
import com.ootd.pickup.images.dto.CreateImageUploadResponse;
import com.ootd.pickup.images.service.ImageService;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ImageController.class)
class ImageControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ImageService imageService;

  @MockitoBean private SlackErrorNotifier slackErrorNotifier;

  @Test
  void 인증된_회원이_업로드_URL을_요청하면_임시_객체키와_필수_헤더를_반환한다() throws Exception {
    CreateImageUploadRequest request =
        new CreateImageUploadRequest(ImagePurpose.CONSIGNMENT, "image/jpeg", 1024);
    CreateImageUploadResponse response =
        new CreateImageUploadResponse(
            "uploads/1/consignments/00000000-0000-0000-0000-000000000001.jpg",
            "https://s3.example.com/upload",
            Map.of("Content-Type", "image/jpeg"),
            Instant.parse("2026-08-01T00:05:00Z"));
    given(imageService.createUpload(1L, request)).willReturn(response);

    mockMvc
        .perform(
            post("/image-uploads")
                .requestAttr(AuthenticationAttributes.ATTRIBUTE_NAME, new Authentication(1L))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "purpose": "CONSIGNMENT",
                      "contentType": "image/jpeg",
                      "contentLength": 1024
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(
            jsonPath("$.temporaryObjectKey")
                .value("uploads/1/consignments/00000000-0000-0000-0000-000000000001.jpg"))
        .andExpect(jsonPath("$.uploadUrl").value("https://s3.example.com/upload"))
        .andExpect(jsonPath("$.requiredHeaders.Content-Type").value("image/jpeg"));
  }

  @Test
  void 허용_크기를_초과하면_업로드_URL을_발급하지_않는다() throws Exception {
    mockMvc
        .perform(
            post("/image-uploads")
                .requestAttr(AuthenticationAttributes.ATTRIBUTE_NAME, new Authentication(1L))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "purpose": "PROFILE",
                      "contentType": "image/png",
                      "contentLength": 10485761
                    }
                    """))
        .andExpect(status().isBadRequest());

    then(imageService)
        .should(never())
        .createUpload(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any());
  }
}
