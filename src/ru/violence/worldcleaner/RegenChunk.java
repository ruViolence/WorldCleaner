package ru.violence.worldcleaner;

import org.bukkit.World;

public class RegenChunk {
    public final World tempWorld;
    public final World realWorld;
    public final int x;
    public final int z;

    public RegenChunk(World tempWorld, World realWorld, int x, int z) {
        this.tempWorld = tempWorld;
        this.realWorld = realWorld;
        this.x = x;
        this.z = z;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RegenChunk that = (RegenChunk) o;
        return this.x == that.x && this.z == that.z && this.tempWorld.equals(that.tempWorld) && this.realWorld.equals(that.realWorld);
    }

    @Override
    public int hashCode() {
        int result = this.x;
        result = 31 * result + this.z;
        result = 31 * result + this.realWorld.hashCode();
        result = 31 * result + this.tempWorld.hashCode();
        return result;
    }
}
