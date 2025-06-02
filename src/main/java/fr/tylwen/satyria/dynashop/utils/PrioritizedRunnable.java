package fr.tylwen.satyria.dynashop.utils;

/**
 * Wrapper pour les tâches avec priorité
 */
public class PrioritizedRunnable implements Runnable, Comparable<PrioritizedRunnable> {
    private final Runnable task;
    private final int priority;

    public PrioritizedRunnable(Runnable task, int priority) {
        this.task = task;
        this.priority = priority;
    }

    @Override
    public void run() {
        task.run();
    }

    public int getPriority() {
        return priority;
    }

    @Override
    public int compareTo(PrioritizedRunnable other) {
        // Priorité inverse: valeur plus élevée = priorité plus élevée
        return Integer.compare(other.priority, this.priority);
    }
}