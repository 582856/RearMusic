package com.zpb.rearmusic;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

final class WhitelistPatch {
    static Map<Object, Object> apply(Map<?, ?> original, boolean enabled, Set<String> packages) {
        Map<Object, Object> result = new HashMap<>(original);
        if (enabled) for (String name : packages) result.put(name, "music");
        return result;
    }
}
