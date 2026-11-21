package org.github.sawervatoryy.mirageRTP.model;

/** Точка отсчёта радиуса для поиска. */
public enum Center {
    /** Спавн мира. */
    SPAWN,
    /** Координаты 0, 0. */
    ZERO,
    /** Текущая позиция игрока (если он в том же мире). */
    PLAYER,
    /** Позиция случайного другого игрока в целевом мире. */
    RANDOM_PLAYER
}
