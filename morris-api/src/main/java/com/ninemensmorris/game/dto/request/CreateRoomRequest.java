package com.ninemensmorris.game.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateRoomRequest(@NotBlank @Size(max = 30) String title) {}
