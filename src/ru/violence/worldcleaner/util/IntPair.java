package ru.violence.worldcleaner.util;

public class IntPair {
    public final int left;
    public final int right;

    public IntPair(int left, int right) {
        this.left = left;
        this.right = right;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        IntPair intPair = (IntPair) o;

        if (this.left != intPair.left) return false;
        return this.right == intPair.right;
    }

    @Override
    public int hashCode() {
        int result = this.left;
        result = 31 * result + this.right;
        return result;
    }
}
