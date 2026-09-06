package com.ninemensmorris.core;

public enum Stone {
    BLACK,
    WHITE;

    public Stone opponent() {
        return this == BLACK ? WHITE : BLACK;
    }
}
