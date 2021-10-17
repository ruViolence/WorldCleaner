package ru.violence.worldcleaner.regen;

public interface RegenTask {
    boolean run();

    int getDone();

    int getTotal();

    String getTaskName();
}
