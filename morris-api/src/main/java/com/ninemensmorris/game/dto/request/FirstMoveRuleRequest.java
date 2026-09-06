package com.ninemensmorris.game.dto.request;

import com.ninemensmorris.game.domain.FirstMoveRule;
import jakarta.validation.constraints.NotNull;

public record FirstMoveRuleRequest(@NotNull FirstMoveRule firstMoveRule) {}
