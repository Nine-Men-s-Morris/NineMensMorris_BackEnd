package com.ninemensmorris.common.exception;

import com.ninemensmorris.common.response.ErrorCode;
import lombok.Builder;
import lombok.Data;
import org.springframework.http.ResponseEntity;

@Data
@Builder
public class CustomErrorResponse {

    private int status;
    private String name;
    private String message;

    public static ResponseEntity<CustomErrorResponse> toResponseEntity(ErrorCode e){
        return ResponseEntity
                .status(e.getStatus())
                .body(CustomErrorResponse.builder()
                        .status(e.getStatus())
                        .name(e.name())
                        .message(e.getMessage())
                        .build());
    }
}
