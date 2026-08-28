package com.example.risingworldstarter.groups;

import java.util.Map;

public record Group(String id, String name, Map<String, GroupMember> members) {
    public Group { members = Map.copyOf(members); }

    public String claimOwnerId() { return "group:" + id; }
}
