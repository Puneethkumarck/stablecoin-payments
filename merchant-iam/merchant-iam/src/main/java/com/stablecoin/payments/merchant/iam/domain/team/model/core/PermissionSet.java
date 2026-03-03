package com.stablecoin.payments.merchant.iam.domain.team.model.core;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;

public final class PermissionSet {

    private final Set<Permission> permissions;

    public PermissionSet(Collection<Permission> permissions) {
        Objects.requireNonNull(permissions, "permissions must not be null");
        this.permissions = Set.copyOf(permissions);
    }

    public static PermissionSet of(Collection<Permission> permissions) {
        return new PermissionSet(permissions);
    }

    public static PermissionSet empty() {
        return new PermissionSet(Set.of());
    }

    public boolean has(Permission required) {
        return permissions.stream().anyMatch(p -> p.implies(required));
    }

    public boolean hasAll(Collection<Permission> required) {
        return required.stream().allMatch(this::has);
    }

    public boolean hasAny(Collection<Permission> required) {
        return required.stream().anyMatch(this::has);
    }

    public Set<Permission> permissions() {
        return permissions;
    }

    public int size() {
        return permissions.size();
    }

    public boolean isEmpty() {
        return permissions.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PermissionSet that)) return false;
        return permissions.equals(that.permissions);
    }

    @Override
    public int hashCode() {
        return permissions.hashCode();
    }

    @Override
    public String toString() {
        return "PermissionSet" + permissions;
    }
}
