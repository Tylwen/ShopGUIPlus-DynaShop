/*
 * ShopGUI+ DynaShop - Dynamic Economy Addon for Minecraft
 * Copyright (C) 2025 Tylwen
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
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