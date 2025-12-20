package com.nonxedy.nonchat.tags;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class Tag {
    private final String id;
    private final String display; // The actual visual tag
    private final String permission;
    private final String category; // Derived from filename
    private final boolean isDefault;
}
