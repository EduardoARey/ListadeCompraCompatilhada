package com.example.listacomprascompartilhada;

import java.util.HashMap;
import java.util.Map;

public class House {
    private String id;
    private String name;
    private String ownerId;
    private String ownerName;
    private String inviteCode;
    private long createdAt;
    private Map<String, HouseMember> members;
    private int memberCount;

    public House() {
        this.members = new HashMap<>();
    }

    public House(String name, String ownerId, String ownerName, String inviteCode) {
        this.name = name;
        this.ownerId = ownerId;
        this.ownerName = ownerName;
        this.inviteCode = inviteCode;
        this.createdAt = System.currentTimeMillis();
        this.members = new HashMap<>();
        this.memberCount = 1;
    }


    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public String getInviteCode() {
        return inviteCode;
    }

    public void setInviteCode(String inviteCode) {
        this.inviteCode = inviteCode;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public Map<String, HouseMember> getMembers() {
        return members;
    }

    public void setMembers(Map<String, HouseMember> members) {
        this.members = members;
    }

    public int getMemberCount() {
        return memberCount;
    }

    public void setMemberCount(int memberCount) {
        this.memberCount = memberCount;
    }

    public static class HouseMember {
        private String userId;
        private String name;
        private String email;
        private long joinedAt;
        private boolean isOwner;

        public HouseMember() {}

        public HouseMember(String userId, String name, String email, boolean isOwner) {
            this.userId = userId;
            this.name = name;
            this.email = email;
            this.isOwner = isOwner;
            this.joinedAt = System.currentTimeMillis();
        }

        // Getters e Setters
        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public long getJoinedAt() {
            return joinedAt;
        }

        public void setJoinedAt(long joinedAt) {
            this.joinedAt = joinedAt;
        }

        public boolean isOwner() {
            return isOwner;
        }

        public void setOwner(boolean owner) {
            isOwner = owner;
        }
    }
}
